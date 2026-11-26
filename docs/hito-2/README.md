# Hito 2 · Parcial 2 de Software II — CoreTech Solutions

Arquitectura, modelado C4 y UML, diseño de datos y código limpio. Todo el contenido se deriva del código real de `prototipo/backend` y de las migraciones Flyway.

| Componente del parcial | Archivo |
|---|---|
| 0. Documento maestro (Word, editable) | [Parcial2-CoreTech-Documento-Maestro.docx](Parcial2-CoreTech-Documento-Maestro.docx) |
| 1. Justificación del estilo arquitectónico | [adr/ADR-001-monolito-modular.md](adr/ADR-001-monolito-modular.md) |
| 1. C4 nivel 1 · Contexto | [c4/1-contexto.mmd](c4/1-contexto.mmd) |
| 1. C4 nivel 2 · Contenedores | [c4/2-contenedores.mmd](c4/2-contenedores.mmd) |
| 1. C4 nivel 3 · Componentes (nombres reales del código) | [c4/3-componentes.mmd](c4/3-componentes.mmd) |
| 2. UML · Clases de dominio | [uml/01-clases-dominio.mmd](uml/01-clases-dominio.mmd) |
| 2. UML · Secuencia full stack con `alt`/`opt` (401, 403, 404, 409, 422) | [uml/02-secuencia-agendar-cita.mmd](uml/02-secuencia-agendar-cita.mmd) |
| 2. UML · Despliegue | [uml/03-despliegue.mmd](uml/03-despliegue.mmd) |
| 3. Diccionario de datos | [diccionario-datos.md](diccionario-datos.md) |
| 3. Coherencia con la API y trazabilidad | [trazabilidad-api.md](trazabilidad-api.md) |
| 3. Mapeo JPA | `backend/src/main/java/co/coretech/portal/*/domain/` (entidades) y `*/repository/` (repositorios) |
| 4. SOLID y patrones, con fragmentos de código | [solid-y-patrones.md](solid-y-patrones.md) |
| 4. ADR-001 Monolito modular | [adr/ADR-001-monolito-modular.md](adr/ADR-001-monolito-modular.md) |
| 4. ADR-002 Control de concurrencia | [adr/ADR-002-control-de-concurrencia.md](adr/ADR-002-control-de-concurrencia.md) |
| 4. ADR-003 Sesión JWT en cookie | [adr/ADR-003-sesion-jwt-en-cookie.md](adr/ADR-003-sesion-jwt-en-cookie.md) |
| 4. ADR-004 Persistencia híbrida JPA/JDBC | [adr/ADR-004-persistencia-hibrida-jpa-jdbc.md](adr/ADR-004-persistencia-hibrida-jpa-jdbc.md) |

## Documento maestro y diagramas exportados

- **`Parcial2-CoreTech-Documento-Maestro.docx`**: el entregable consolidado en Word, editable (portada, justificación del estilo, C4, UML, diccionario de datos, SOLID y patrones, y los ADRs). Se puede exportar a PDF desde Word con *Archivo → Exportar*.
- **`img/`**: los 6 diagramas exportados a PNG (para el documento) y a SVG (vectorial, sin pérdida de calidad).

## Cómo regenerar los diagramas

Los `.mmd` son la fuente (Mermaid). Se pueden editar y pegar en <https://mermaid.live>, abrir con la vista previa de Markdown de VS Code (extensión Mermaid) o exportar con `npx @mermaid-js/mermaid-cli -i archivo.mmd -o archivo.png`. GitHub también los muestra si se incrustan en un bloque ```` ```mermaid ````.
