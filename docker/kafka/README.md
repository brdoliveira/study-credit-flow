# Broker local

O Compose usa Redpanda em modo single-node, compatível com o protocolo Kafka.
A aplicação o acessa internamente em `kafka:9092`; da máquina host, use
`localhost:${KAFKA_PORT:-9092}`.

Os dados do broker ficam no volume nomeado `kafka_data`. Para reiniciar o
ambiente preservando eventos, execute `docker compose down`. Para apagar dados
locais deliberadamente, execute `docker compose down --volumes`.

O health check usa `rpk cluster health`, por isso a aplicação só inicia depois
que o broker estiver pronto.
