# Gates da especificação

O workflow `.github/workflows/ci.yml` trata a qualidade como um bloqueio antes
da imagem de contêiner. O job `quality-gates` executa, nesta ordem:

1. compilação Kotlin;
2. testes Gradle;
3. análise estática Detekt com `config/detekt/detekt.yml`;
4. testes de contrato Node (`node --test`);
5. auditoria spec-anchored (`npx --yes onp-spec audit --ci`).

O job `container-image` depende de `quality-gates`. Como os steps não usam
`continue-on-error`, a imagem só pode ser criada quando todos os gates passam.

## Execução local

Para validar o contrato do pipeline, execute:

```sh
node --test
```

Para reproduzir os gates de aplicação quando o wrapper Gradle estiver
disponível, execute os mesmos comandos do workflow. A auditoria continua sendo
o veredito da especificação e deve ser chamada com `audit --ci`.
