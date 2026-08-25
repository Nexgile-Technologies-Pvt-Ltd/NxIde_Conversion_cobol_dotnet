# CardDemo backend

Spring Boot 3.4 service on Java 21. REST API, COBOL business logic, batch jobs and the data
migration that loads the shipped COBOL data sets into PostgreSQL.

See [`../README.md`](../README.md) for the full picture and
[`../CONVERSION-MAP.md`](../CONVERSION-MAP.md) for the program-by-program mapping.

## Run

```powershell
mvn spring-boot:run          # http://localhost:8080
mvn test                     # 44 characterisation tests
mvn -DskipTests package      # target/carddemo-backend.jar
java -jar target/carddemo-backend.jar
```

## Package layout

| Package | Contents |
|---|---|
| `common` | `CobolText` fixed-width/overpunch codec, `ApiException` |
| `config` | Security, JWT, CORS, OpenAPI, externalised properties |
| `domain` | JPA entities, one per COBOL copybook |
| `repository` | Spring Data repositories, including the keyset paging queries that replace CICS browse |
| `dto` | Request and response records, one group per screen |
| `validation` | `CobolDateValidator` (`CSUTLDPY`/`CSUTLDTC`), `CobolFieldValidator` (`COACTUPC` edits), `LookupTables` (`CSLKPCDY`) |
| `service` | Online programs: auth, menus, accounts, cards, transactions, bill payment, users, reference data |
| `batch` | `POSTTRAN`, `INTCALC`, `TRANREPT`, `CREASTMT` equivalents plus run bookkeeping |
| `migration` | COBOL fixture readers and the startup migration runner |
| `web` | REST controllers and the global exception handler |

## Configuration

Everything lives in `src/main/resources/application.yml` and can be overridden by environment
variables.

| Key | Environment variable | Default |
|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://217.217.251.161:8100/cobol_to_java_app` |
| `spring.datasource.username` | `DB_USER` | `cobol_to_java_user` |
| `spring.datasource.password` | `DB_PASSWORD` | (set in the file) |
| `carddemo.jwt.secret` | `CARDDEMO_JWT_SECRET` | development value — **replace in any real deployment** |
| `carddemo.migration.source-directory` | `COBOL_DATA_DIR` | empty; the bundled `classpath:cobol-data` copies are used |
| `carddemo.migration.legacy-password` | `CARDDEMO_LEGACY_PASSWORD` | `PASSWORD1` |
| `server.port` | `SERVER_PORT` | `8080` |

`carddemo.migration.source-directory` can point at
`../../Cobol_Code/aws-mainframe-modernization-carddemo/app/data` to read the original files rather
than the bundled copies; both contain the same bytes.

## Schema

Flyway owns the schema (`db/migration/V1__carddemo_schema.sql`); Hibernate runs with
`ddl-auto: validate` and only checks that the entities match. Add changes as new `V2__…` migrations
rather than editing `V1`.
