import json
from pathlib import Path
import tempfile
import unittest

from ventas_spark_job import ejecutar_pipeline, map_clientes_rfm, seudonimizar_cliente


class VentasSparkJobTest(unittest.TestCase):
    def setUp(self) -> None:
        self.sample_path = str(Path(__file__).with_name("sample_ventas.ndjson"))

    def test_mapreduce_ignora_pedidos_cancelados_o_no_aprobados(self) -> None:
        resultado = ejecutar_pipeline(self.sample_path, modo="mapreduce")
        self.assertEqual(resultado["motor"], "mapreduce-local")
        categorias = {(r["moneda"], r["categoria"]): r for r in resultado["ventas_por_categoria"]}

        self.assertEqual(categorias[("PEN", "Smartphones")]["ingresos"], 4999.00)
        self.assertEqual(categorias[("PEN", "Audio y Sonido")]["ingresos"], 2598.00)
        self.assertEqual(categorias[("PEN", "Audio y Sonido")]["unidades"], 2)
        self.assertEqual(categorias[("USD", "Monitores")]["unidades"], 2)
        self.assertEqual(categorias[("PEN", "Periféricos")]["ingresos"], 599.00)

    def test_rfm_resume_segmentos_sin_exponer_pii_en_salida(self) -> None:
        resultado = ejecutar_pipeline(self.sample_path, modo="mapreduce")
        segmentos = {s["segmento"]: s for s in resultado["resumen_segmentos_rfm"]}

        self.assertEqual(segmentos["VIP"]["clientes"], 1)
        self.assertEqual(segmentos["VIP"]["gasto_total_pen"], 6298.00)
        self.assertEqual(segmentos["NUEVO"]["clientes"], 2)
        self.assertNotIn("ana.perez@gmail.com", json.dumps(resultado))

    def test_maneja_nulos_y_rechaza_nan_y_rutas_distribuidas_sin_spark(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            nulos_file = Path(tmp) / "nulos.ndjson"
            nulos_file.write_text(
                json.dumps({
                    "pedido_id": 2001,
                    "estado_pago": "APROBADO",
                    "estado_pedido": None,
                    "items": None,
                    "total_base_pen": None,
                }) + "\n",
                encoding="utf-8",
            )
            res = ejecutar_pipeline(str(nulos_file), modo="mapreduce")
            self.assertEqual(res["ventas_por_categoria"], [])
            self.assertEqual(res["resumen_segmentos_rfm"][0]["clientes"], 1)

            nan_file = Path(tmp) / "nan.ndjson"
            nan_file.write_text('{"estado_pago":"APROBADO","total_base_pen":NaN}\n', encoding="utf-8")
            with self.assertRaises(ValueError):
                ejecutar_pipeline(str(nan_file), modo="mapreduce")

        with self.assertRaises(RuntimeError):
            ejecutar_pipeline("gs://bucket/ventas.ndjson", modo="mapreduce")

    def test_escribe_resumen_en_directorio_salida(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            ejecutar_pipeline(self.sample_path, modo="mapreduce", ruta_salida=tmp)
            out_file = Path(tmp) / "resumen_analitico.json"
            self.assertTrue(out_file.exists())
            data = json.loads(out_file.read_text(encoding="utf-8"))
            self.assertIn("ventas_por_categoria", data)
            self.assertIn("resumen_segmentos_rfm", data)

    def test_seudonimiza_emails_clientes_sin_pii(self) -> None:
        hash_mayus = seudonimizar_cliente("Ana.Perez@Gmail.com ")
        hash_minus = seudonimizar_cliente("ana.perez@gmail.com")
        self.assertEqual(hash_mayus, hash_minus)
        self.assertEqual(len(hash_minus), 64)
        pares = list(map_clientes_rfm({
            "estado_pago": "APROBADO",
            "estado_pedido": "PAGADO",
            "cliente_email": "ana.perez@gmail.com",
            "total_base_pen": 100.0,
            "fecha": "2026-11-01T10:00:00",
        }))
        self.assertEqual(len(pares), 1)
        self.assertEqual(pares[0][0], hash_minus)
        self.assertNotIn("@", pares[0][0])


if __name__ == "__main__":
    unittest.main()
