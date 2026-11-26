# Analítica Distribuida Batch (Unidad 2 · Semana 6)

Este módulo complementa la arquitectura transaccional (OLTP en PostgreSQL / Cloud SQL) con un flujo de **procesamiento distribuido batch** basado en **MapReduce / Apache Spark** sobre almacenamiento de objetos distribuido (**Google Cloud Storage `gs://` o HDFS**).

## Arquitectura OLTP → Data Lake → Spark

```
[PostgreSQL / Cloud SQL] ──(Exportación NDJSON nocturna)──► [GCS / HDFS: gs://electronix-analytics/]
                                                                       │
                                                       (spark-submit / Cloud Dataproc)
                                                                       ▼
                                                        [Apache Spark / MapReduce Job]
                                                         - Fase Map / Shuffle / Reduce
                                                         - Salida Parquet particionada
```

## Ejecución local (MapReduce en Python estándar)

```bash
python3 analytics/ventas_spark_job.py --modo mapreduce
```

## Ejecución de pruebas unitarias

```bash
python3 -m unittest discover -s analytics -p "test_*.py" -v
```

## Ejecución en clúster Apache Spark / Google Cloud Dataproc

```bash
spark-submit \
  --master yarn \
  analytics/ventas_spark_job.py \
  --modo spark \
  --input gs://TU_BUCKET_ANALITICA/exports/ventas.ndjson \
  --output gs://TU_BUCKET_ANALITICA/curated/
```
