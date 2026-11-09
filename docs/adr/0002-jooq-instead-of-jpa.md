# jOOQ for data access instead of Spring Data JPA

Data access uses jOOQ with Java types generated from the database schema, not Spring Data JPA / Hibernate (Spring Boot's default). The database schema is the source of truth rather than entity classes. That suits a project whose most interesting queries are statistics (aggregations, rankings, window functions) that SQL expresses directly. jOOQ is also a deliberate learning choice (ADR 0001). As a result there is no entity lifecycle, dirty checking or lazy loading: every query that reads or writes is explicit.
