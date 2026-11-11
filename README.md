# Seatmap

**Seatmap** is a comprehensive, desktop-based exam seat allocation system designed for educational institutions. Built with JavaFX and powered by a PostgreSQL backend, it streamlines the entire examination process—from bulk student imports and room management to automated seating arrangements. Its core allocation engine features anti-cheating algorithms to ensure students from the same department are not seated next to each other. Includes interactive visual grids, PDF seating chart generation, and secure admin authentication.

## Prerequisites

- Java 17+
- Apache Maven 3.8+
- PostgreSQL (running locally on port `5432`, or configured in `config.properties`)

## Configuration

Create `src/main/resources/config.properties` (ignored by Git) with your PostgreSQL connection details:

```properties
db.host=localhost
db.port=5432
db.name=seatmap_db
db.user=postgres
db.password=your_password
```

## How to Run

Compile the project and launch the application with the JavaFX Maven plugin:

```bash
mvn clean compile
mvn javafx:run
```

Run the test suite with:

```bash
mvn test
```
