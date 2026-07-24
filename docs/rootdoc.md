# world

Representation, validation, exact computation, and locale-correct presentation of
real-world domain concepts, cross-published for the JVM, Scala.js, and Scala Native.

Operations return errors as values from owned sealed families under `WorldError`, and no
module depends on an external library or on a platform locale, formatting, or time
facility.

Module concerns, the dependency graph, and artefact coordinates are on the
[documentation site](https://dev.shuwari.africa/world/).

This release carries the build, publishing, and documentation pipeline; the API arrives
with the increments that implement it.
