#!/usr/bin/env python3
"""
Procesamiento distribuido de ventas Electronix (Unidad 2 - Semana 6).

Soporta dos modos de ejecución sobre archivos NDJSON exportados (locales o gs:// / hdfs://):
1. Apache Spark (PySpark): ejecución distribuida en clúster (Dataproc / Hadoop YARN / Spark Standalone).
2. Motor MapReduce en Python estándar (--modo mapreduce): útil para validación local sin clúster Spark.
"""

from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
import math
import os
from pathlib import Path
from typing import Any, Callable, Iterable

DISTRIBUTED_PREFIXES = ("gs://", "hdfs://", "s3://", "s3a://")


def _parse_no_nan(constant: str) -> Any:
    raise ValueError(f"Valor JSON no numérico finito no permitido: {constant}")


def _to_finite_float(valor: Any, default: float = 0.0) -> float:
    if valor is None or isinstance(valor, bool):
        return default
    num = float(valor)
    if not math.isfinite(num):
        raise ValueError(f"Número no finito detectado: {valor!r}")
    return num


def _to_int(valor: Any, default: int = 0) -> int:
    if valor is None or isinstance(valor, bool):
        return default
    return int(valor)


def leer_ndjson_local(ruta: str) -> list[dict[str, Any]]:
    if ruta.startswith(DISTRIBUTED_PREFIXES):
        raise RuntimeError(
            f"La ruta '{ruta}' requiere PySpark (--modo spark) o una copia local descargada."
        )
    registros: list[dict[str, Any]] = []
    with open(ruta, "r", encoding="utf-8") as fh:
        for linea in fh:
            linea = linea.strip()
            if linea:
                registros.append(json.loads(linea, parse_constant=_parse_no_nan))
    return registros


def map_ventas_por_categoria(registro: dict[str, Any]) -> Iterable[tuple[tuple[str, str], tuple[float, int]]]:
    """Fase MAP: emite ((moneda, categoria), (subtotal, cantidad)) por cada ítem de pedido pagado."""
    if registro.get("estado_pago") != "APROBADO" or registro.get("estado_pedido") == "CANCELADO":
        return []
    items = registro.get("items")
    if not isinstance(items, list):
        return []
    moneda = str(registro.get("moneda") or "PEN")
    pares: list[tuple[tuple[str, str], tuple[float, int]]] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        categoria = str(item.get("categoria") or "Sin categoría")
        cantidad = _to_int(item.get("cantidad"), 0)
        subtotal = _to_finite_float(item.get("subtotal"), 0.0)
        pares.append(((moneda, categoria), (subtotal, cantidad)))
    return pares


def seudonimizar_cliente(email: Any) -> str:
    """Seudonimiza identificadores de clientes mediante SHA-256 con sal opcional."""
    normalizado = str(email or "anonimo").strip().lower() or "anonimo"
    salt = os.environ.get("ANALYTICS_HASH_SALT", "")
    return hashlib.sha256(f"{salt}{normalizado}".encode("utf-8")).hexdigest()


def map_clientes_rfm(registro: dict[str, Any]) -> Iterable[tuple[str, tuple[float, int, str]]]:
    """Fase MAP: emite (cliente_key_hash, (total_base_pen, 1, fecha)) para segmentación de valor sin PII."""
    if registro.get("estado_pago") != "APROBADO" or registro.get("estado_pedido") == "CANCELADO":
        return []
    cliente_key = seudonimizar_cliente(registro.get("cliente_email"))
    total_pen = _to_finite_float(registro.get("total_base_pen"), 0.0)
    fecha = str(registro.get("fecha") or "")[:10]
    return [(cliente_key, (total_pen, 1, fecha))]


def shuffle_y_agrupar(pares: Iterable[tuple[Any, Any]]) -> dict[Any, list[Any]]:
    """Fase SHUFFLE & SORT: agrupa valores intermedios por clave."""
    agrupado: dict[Any, list[Any]] = defaultdict(list)
    for clave, valor in pares:
        agrupado[clave].append(valor)
    return dict(sorted(agrupado.items(), key=lambda kv: str(kv[0])))


def reduce_ventas_por_categoria(clave: tuple[str, str], valores: list[tuple[float, int]]) -> dict[str, Any]:
    """Fase REDUCE: consolida ingresos y unidades vendidas por moneda y categoría."""
    ingresos = round(sum(v[0] for v in valores), 2)
    unidades = sum(v[1] for v in valores)
    return {
        "moneda": clave[0],
        "categoria": clave[1],
        "ingresos": ingresos,
        "unidades": unidades,
    }


def reduce_clientes_rfm(clave: str, valores: list[tuple[float, int, str]]) -> dict[str, Any]:
    """Fase REDUCE: calcula gasto acumulado, frecuencia y segmento RFM."""
    gasto_total = round(sum(v[0] for v in valores), 2)
    frecuencia = sum(v[1] for v in valores)
    ultima_compra = max((v[2] for v in valores if v[2]), default="")
    segmento = "VIP" if gasto_total >= 5000 or frecuencia >= 3 else ("RECURRENTE" if frecuencia >= 2 else "NUEVO")
    return {
        "gasto_pen": gasto_total,
        "pedidos": frecuencia,
        "ultima_compra": ultima_compra,
        "segmento": segmento,
    }


def _resumir_segmentos(clientes: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    acumulado: dict[str, dict[str, Any]] = {}
    for c in clientes:
        seg = c["segmento"]
        bucket = acumulado.setdefault(seg, {"segmento": seg, "clientes": 0, "gasto_total_pen": 0.0})
        bucket["clientes"] += 1
        bucket["gasto_total_pen"] = round(bucket["gasto_total_pen"] + c["gasto_pen"], 2)
    return sorted(acumulado.values(), key=lambda r: r["gasto_total_pen"], reverse=True)


def ejecutar_mapreduce(
    registros: Iterable[dict[str, Any]],
    mapper: Callable[[dict[str, Any]], Iterable[tuple[Any, Any]]],
    reducer: Callable[[Any, list[Any]], dict[str, Any]],
) -> list[dict[str, Any]]:
    emitidos: list[tuple[Any, Any]] = []
    for reg in registros:
        emitidos.extend(mapper(reg))
    agrupados = shuffle_y_agrupar(emitidos)
    return [reducer(k, vals) for k, vals in agrupados.items()]


def ejecutar_con_spark(ruta_entrada: str, ruta_salida: str | None = None) -> dict[str, Any]:
    """Ejecuta el pipeline analítico distribuido usando PySpark DataFrames sin exponer PII en logs."""
    from pyspark.sql import SparkSession
    from pyspark.sql import functions as F

    spark = SparkSession.builder.appName("ElectronixSalesAnalytics").getOrCreate()
    try:
        df = spark.read.json(ruta_entrada)
        validos = df.filter(
            (F.col("estado_pago") == "APROBADO")
            & (F.coalesce(F.col("estado_pedido"), F.lit("")) != "CANCELADO")
        )

        por_categoria = (
            validos.filter(F.col("items").isNotNull())
            .select(F.coalesce(F.col("moneda"), F.lit("PEN")).alias("moneda"), F.explode("items").alias("item"))
            .groupBy("moneda", F.coalesce(F.col("item.categoria"), F.lit("Sin categoría")).alias("categoria"))
            .agg(
                F.round(F.sum(F.coalesce(F.col("item.subtotal"), F.lit(0.0))), 2).alias("ingresos"),
                F.sum(F.coalesce(F.col("item.cantidad"), F.lit(0))).cast("long").alias("unidades"),
            )
            .orderBy(F.desc("ingresos"))
        )

        hash_salt = os.environ.get("ANALYTICS_HASH_SALT", "")
        cliente_pseudonimo = F.sha2(
            F.concat(
                F.lit(hash_salt),
                F.lower(F.trim(F.coalesce(F.col("cliente_email"), F.lit("anonimo")))),
            ),
            256,
        ).alias("cliente_key")

        rfm_clientes = (
            validos.groupBy(cliente_pseudonimo)
            .agg(
                F.round(F.sum(F.coalesce(F.col("total_base_pen"), F.lit(0.0))), 2).alias("gasto_pen"),
                F.count("*").alias("pedidos"),
                F.max(F.substring(F.coalesce(F.col("fecha"), F.lit("")), 1, 10)).alias("ultima_compra"),
            )
            .withColumn(
                "segmento",
                F.when((F.col("gasto_pen") >= 5000) | (F.col("pedidos") >= 3), "VIP")
                .when(F.col("pedidos") >= 2, "RECURRENTE")
                .otherwise("NUEVO"),
            )
        )

        # Solo colecta agregados acotados por segmento y categoría (evita OOM y PII en driver stdout)
        segmentos_resumen = (
            rfm_clientes.groupBy("segmento")
            .agg(
                F.count("*").alias("clientes"),
                F.round(F.sum("gasto_pen"), 2).alias("gasto_total_pen"),
            )
            .orderBy(F.desc("gasto_total_pen"))
        )

        if ruta_salida:
            por_categoria.write.mode("overwrite").parquet(f"{ruta_salida.rstrip('/')}/ventas_por_categoria")
            rfm_clientes.write.mode("overwrite").parquet(f"{ruta_salida.rstrip('/')}/clientes_rfm")

        return {
            "motor": "pyspark",
            "ventas_por_categoria": [r.asDict() for r in por_categoria.collect()],
            "resumen_segmentos_rfm": [r.asDict() for r in segmentos_resumen.collect()],
        }
    finally:
        spark.stop()


def ejecutar_pipeline(ruta_entrada: str, modo: str = "auto", ruta_salida: str | None = None) -> dict[str, Any]:
    if modo == "spark":
        return ejecutar_con_spark(ruta_entrada, ruta_salida)

    if modo == "auto":
        try:
            import pyspark  # noqa: F401
            return ejecutar_con_spark(ruta_entrada, ruta_salida)
        except ImportError:
            pass

    if ruta_salida and ruta_salida.startswith(DISTRIBUTED_PREFIXES):
        raise RuntimeError(f"La ruta de salida '{ruta_salida}' requiere PySpark (--modo spark).")

    registros = leer_ndjson_local(ruta_entrada)
    ventas_cat = sorted(
        ejecutar_mapreduce(registros, map_ventas_por_categoria, reduce_ventas_por_categoria),
        key=lambda r: r["ingresos"],
        reverse=True,
    )
    clientes_rfm = ejecutar_mapreduce(registros, map_clientes_rfm, reduce_clientes_rfm)
    resultado = {
        "motor": "mapreduce-local",
        "ventas_por_categoria": ventas_cat,
        "resumen_segmentos_rfm": _resumir_segmentos(clientes_rfm),
    }
    if ruta_salida:
        out_dir = Path(ruta_salida)
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "resumen_analitico.json").write_text(
            json.dumps(resultado, indent=2, ensure_ascii=False), encoding="utf-8"
        )
    return resultado


def main() -> None:
    parser = argparse.ArgumentParser(description="Analítica distribuida MapReduce / Spark para Electronix")
    parser.add_argument(
        "--input",
        default=str(Path(__file__).with_name("sample_ventas.ndjson")),
        help="Ruta local o URI gs:// / hdfs:// al dataset NDJSON de pedidos",
    )
    parser.add_argument("--output", default=None, help="Directorio o URI gs:// de salida")
    parser.add_argument(
        "--modo",
        choices=["auto", "spark", "mapreduce"],
        default="auto",
        help="Motor de ejecución",
    )
    args = parser.parse_args()
    resumen = ejecutar_pipeline(args.input, modo=args.modo, ruta_salida=args.output)
    print(json.dumps(resumen, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
