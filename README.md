# Named Parameters in Java

Java APIs are sometimes designed with several overloaded versions of the same method, all to enable some parameters to be omitted.
In other languages, such as Common Lisp, a function with optional parameters exists in a single form but can be called with varying number of arguments.
Furthermore, the caller may specify the optional arguments in any order by explicitly including the names of the corresponding parameters.

This project aims to enhance Java with named parameters so that:

1. A single method may be declared, but be called with some of the arguments omitted.

2. Default values may be specified for optional parameters.

3. Method arguments can be specified in any order by explicit naming of each.

This aim is achieved by generating source code at compile-time via an annotation processor.
