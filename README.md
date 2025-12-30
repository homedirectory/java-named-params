# Named Parameters in Java

Java APIs are sometimes designed with several overloaded versions of the same method, all to enable some parameters to have default values.
In other languages, such as Common Lisp, function parameters can be declared optional so that a single fuction definition is sufficient.
Furthermore, the caller may specify optional arguments in any order by explicitly including the names of the corresponding parameters.

This project aims to enhance Java with named parameters so that:

1. A single method may be declared, but be called with some of the arguments omitted.

2. Default values may be specified for optional parameters.

3. Method arguments can be specified in any order by explicitly naming each.

This aim is achieved by generating source code at compile-time using annotation processing.

## Usage

### Initial configuration

First of all, enable the annotation processor.

For Maven projects, include the following dependency.

```xml
<dependency>
  <groupId>homedir</groupId>
  <artifactId>named-params</artifactId>
  <version>...</version>
</dependency>
```

Then, configure the compiler.

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <generatedSourcesDirectory>${project.build.directory}/generated-sources</generatedSourcesDirectory>
    <annotationProcessors>
      <annotationProcessor>homedir.named_params.processor.NamedParametersProcessor</annotationProcessor>
    </annotationProcessors>
  </configuration>
</plugin>
```

### Code generation

The annotation processor recognises methods annotated with `@NamedParameters` and generates corresponding methods with named parameters.

#### Static methods

```java
@NamedParameters("join")
static String _join(Collection<Object> objects, String delim, @Named String prefix, @Named String suffix) {
    return objects.stream()
            .map(Object::toString)
            .collect(joining(delim, prefix == null ? "" : prefix, suffix == null ? "" : suffix));
}
```

Upon seeing method `_join`, the processor will generate an interface that contains static method `join`, whose name is specified in `@NamedParameters`, and supports 2 named parameters: `prefix` and `suffix`.

The generated method `join` can then be used as follows.

```java
// Imports join, prefix, suffix.
import static homedir.named_params.test.Sources$join_NamedParams.*;

join(List.of(1, "two", 3), ", ");                           // => "1, two, 3"
join(List.of(1, "two", 3), ", ", prefix, "[", suffix, "]"); // => "[1, two, 3]"
join(List.of(1, "two", 3), ", ", prefix, "list: ");         // => "list: 1, two, 3"
join(List.of(1, "two", 3), ", ", suffix, ".");              // => "1, two, 3."
```

Parameters `objects` and `delim` are mandatory in all cases.
Named parameters `prefix` and `suffix` are specified using _argument pairs_ "name, value", and can be specified in any order (e.g., `suffix` before `prefix`), or can be left unspecified.

Named parameters that have not been given any value (i.e., an argument pair was not specified), default to `null`.
This can be seen in the implementation of `_join`, which handles the case of `null` arguments.

Here is the entire interface generated for `_join`:

```java
public interface Sources$join_NamedParams {
  interface Param<T> {}
  Param<String> prefix = new Param<>(){};
  Param<String> suffix = new Param<>(){};

  static String join(Collection<Object> objects, String delim) {
    String _prefix = null;
    String _suffix = null;
    return Sources._join(objects, delim, _prefix, _suffix);
  }

  static <T1> String join(Collection<Object> objects, String delim, Param<T1> p1, T1 v1) {
    String _prefix = null;
    if (p1 == prefix) _prefix = (String) v1;
    String _suffix = null;
    if (p1 == suffix) _suffix = (String) v1;
    return Sources._join(objects, delim, _prefix, _suffix);
  }

  static <T1, T2> String join(Collection<Object> objects, String delim, Param<T1> p1, T1 v1, Param<T2> p2, T2 v2) {
    String _prefix = null;
    if (p1 == prefix) _prefix = (String) v1;
    else if (p2 == prefix) _prefix = (String) v2;
    String _suffix = null;
    if (p1 == suffix) _suffix = (String) v1;
    else if (p2 == suffix) _suffix = (String) v2;
    return Sources._join(objects, delim, _prefix, _suffix);
  }
}
```

#### Instance methods

Instance methods are processed very similarly to static methods, but generated interfaces will contain instance methods instead of static ones.
This means that the class declaring the annotated instance method should implement the generated interface so that the generated methods can be called on that class.

For example, consider the following declaration of `Sequence.find` with named parameters.

```java
class Sequence<T> {

  /// Searches this sequence for `item`.
  ///
  /// @param test    if present, this predicate is used to compare elements of this sequence with `item`.
  ///                Otherwise, [Object#equals] is used.
  /// @param fromEnd if `true`, this sequence is searched from the end.
  /// @param start   if present, the sub-sequence beginning at this index is searched.
  /// @param end     if present, the sub-sequence before this index is searched.
  /// @return an element of this sequence that matches `item`, if found, otherwise `null`.
  @NamedParameters
  protected <X> T find(X item, @Named BiPredicate<X, T> test, @Named Boolean fromEnd, @Named Integer start, @Named Integer end) {
```

The annotation processor will generate an interface that will then have to be implemented by `Sequence`.

```java
class Sequence<T> implements Sequence$find_NamedParams<T> {
```

Note that the generated interface has the same type parameters as `Sequence`, namely `T`, to match the original signature of `find`.

Generated method `find` can then be called on `Sequence`.

```java
var seq = new Sequence<>("one", "bat", "gb", "cpu", "mem");

seq.find("one");
// => "one"

seq.find("one",
         test, (x1, x2) -> ((String) x1).length() == ((String) x2).length(),
         fromEnd, true);
// => "mem"

seq.find("one",
         test, (x1, x2) -> ((String) x1).length() == ((String) x2).length(),
         start, 1, end, 3);
// => "bat"
```

Note that when specifying a predicate for parameter `test`, explicit type casting had to be used.
Lack of type inference for generic named parameters is one of the limitations of the current approach.

#### Primitive types

As mentioned above, when named parameters have reference types (e.g., `String`), the default value that is used in absence of a corresponding argument pair is `null`.
For primitive types, `null` cannot be used, so the default value must be specified explicitly.

```java
<X> T find(X item,
           @Named BiPredicate<X, T> test,
           @Named(defaultBool = false) boolean fromEnd,
           @Named(defaultInt = 0) int start,
           @Named(defaultInt = -1) int end)
```

Above is a version of `Sequence.find` that uses primitive types for named parameters.

In some cases, this will not suffice to detect whether an argument has been specified for a named parameter.
Specifically, when the whole domain of primitive values (e.g., all integers) is considered valid.
Then, there is no room for a special value that could designate the absence of an argument.
Currently, this is one of the limitations, but it could be easily solved in the fashion of `supplied-p-parameter` in Common Lisp.
