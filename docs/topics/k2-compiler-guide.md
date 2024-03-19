[//]: # (title: K2 compiler migration guide)

> The new K2 compiler is enabled by default from Kotlin 2.0.0.
>
{type="note"}

As the Kotlin language and ecosystem have continued to evolve, so has the Kotlin compiler. Firstly, by introducing new 
JVM and JS IR (Intermediate Representation) backends that share logic, simplifying code generation for targets on different
platforms. Now, the next stage of its evolution introduces a new frontend known as K2.

The K2 compiler frontend has been completely rewritten, featuring a new, more efficient architecture. The fundamental 
change in the frontend is the use of one unified data structure that contains more semantic information. The frontend 
is responsible for performing semantic analysis, call resolution, and type inference. The new architecture and enriched 
data structure enables the K2 compiler to provide the following benefits:

* **Offers improved and optimized call resolution and type inference**

  The compiler behaves more consistently and understands your code better.
* **Facilitates introduction of new syntactic sugar for new language features**

  In the future, you'll be able to use more concise, readable code when new features are introduced.
* **Reduces compilation times**

  Compilation times can be decreased up to 46%. For more information, see [Performance](#performance).
* **Enhances IDE performance**

  If you enable K2 mode in IntelliJ IDEA, then IntelliJ IDEA will use the K2 compiler frontend to analyze your Kotlin code,
  bringing stability and performance improvements. For more information, see [How to enable IDE support](#how-to-enable-ide-support).

  > The K2 Kotlin mode is in Alpha. The performance and stability of code highlighting and code completion have been improved,
  > but not all IDE features are supported yet.
  > 
  {type="warning"}

As a result, we've already made improvements to some [language features](#language-feature-improvements).

This guide:
* explains the benefits of the new K2 compiler
* highlights changes you might encounter during migration and how to adapt your code accordingly
* describes how you can roll back to the previous version

## Language feature improvements

The Kotlin K2 compiler provides language feature improvements in [smart casts](#smart-casts) and [Kotlin Multiplatform](#kotlin-multiplatform).

### Smart casts

The Kotlin compiler can automatically cast an object to a type in specific cases,
saving you the trouble of having to explicitly cast it yourself. This is called [smart casting](typecasts.md#smart-casts).
The Kotlin K2 compiler now performs smart casts in even more scenarios than before.

In Kotlin 2.0.0, we've made improvements related to smart casts in the following areas:
* [Local variables and further scopes](#local-variables-and-further-scopes)
* [Type checks with logical `or` operator](#type-checks-with-logical-or-operator)
* [Inline functions](#inline-functions)
* [Properties with function types](#properties-with-function-types)
* [Exception handling](#exception-handling)
* [Increment and decrement operators](#increment-and-decrement-operators)

#### Local variables and further scopes

Previously, if a variable was evaluated as not `null` within an `if` condition,
the variable was smart cast, and information about this variable was shared further within the scope of the `if` block.
However, if you declared the variable **outside** the `if` condition, no information about the variable was available
within the `if` condition, so it couldn't be smart cast. This behavior was also seen with `when` expressions and `while` loops.

From Kotlin 2.0.0, if you declare a variable before using it in your `if`, `when`, or `while` condition
then any information collected by the compiler about the variable is accessible in the condition statement and its block for smart casting.

This can be useful when you want to do things like extract boolean conditions into variables.
Then, you can give the variable a meaningful name, which makes your code easier to read, and easily reuse the variable later in your code.
For example:

```kotlin
class Cat {
    fun purr() {
        println("Purr purr")
    }
}

fun petAnimal(animal: Any) {
    val isCat = animal is Cat
    if (isCat) {
        // In Kotlin 2.0.0, the compiler can access
        // information about isCat, so it knows that
        // isCat was smart cast to type Cat.
        // Therefore, the purr() function is successfully called.
        // In Kotlin 1.9.20, the compiler doesn't know
        // about the smart cast, so calling the purr()
        // function triggers an error.
        animal.purr()
    }
}

fun main(){
    val kitty = Cat()
    petAnimal(kitty)
    // Purr purr
}
```
{kotlin-runnable="true" kotlin-min-compiler-version="2.0" id="kotlin-smart-casts-k2-local-variables-guide" validate="false"}

#### Type checks with logical `or` operator

In Kotlin 2.0.0, if you combine type checks for objects with an `or` operator (`||`), then a smart cast
is made to their closest common supertype. Before this change, a smart cast was always made to `Any` type.

In this case, you still had to manually check the type of the object afterward before you could access any of its properties or call its functions.

For example:

```kotlin
interface Status {
    fun signal() {}
}

interface Ok : Status
interface Postponed : Status
interface Declined : Status

fun signalCheck(signalStatus: Any) {
    if (signalStatus is Postponed || signalStatus is Declined) {
        // signalStatus is smart cast to a common supertype Status
        signalStatus.signal()
        // Prior to Kotlin 2.0.0, signalStatus is smart cast 
        // to type Any, so calling the signal() function triggered an
        // Unresolved reference error. The signal() function can only 
        // be called successfully after another type check:
        
        // check(signalStatus is Status)
        // signalStatus.signal()
    }
}
```

> The common supertype is an **approximation** of a union type. [Union types](https://en.wikipedia.org/wiki/Union_type)
> are not supported in Kotlin.
>
{type="note"}

#### Inline functions

In Kotlin 2.0.0, the K2 compiler treats inline functions differently,
allowing it to determine in combination with other compiler analyses whether it's safe to smart cast.

Specifically, inline functions are now treated as having an implicit
[`callsInPlace`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.contracts/-contract-builder/calls-in-place.html) contract.
So any lambda functions passed to an inline function are called "in place". Since lambda functions are called in place,
the compiler knows that a lambda function can't leak references to any variables contained within its function body.
The compiler uses this knowledge along with other compiler analyses to decide if it's safe to smart cast any of the captured variables.
For example:

```kotlin
interface Processor {
    fun process()
}

inline fun inlineAction(f: () -> Unit) = f()

fun nextProcessor(): Processor? = null

fun runProcessor(): Processor? {
    var processor: Processor? = null
    inlineAction {
        // In Kotlin 2.0.0, the compiler knows that processor 
        // is a local variable, and inlineAction() is an inline function, so 
        // references to processor can't be leaked. Therefore, it's safe 
        // to smart cast processor.
      
        // If processor isn't null, processor is smart cast
        if (processor != null) {
            // The compiler knows that processor isn't null, so no safe call 
            // is needed
            processor.process()

            // In Kotlin 1.9.20, you have to perform a safe call:
            // processor?.process()
        }

        processor = nextProcessor()
    }

    return processor
}
```
#### Properties with function types

In previous versions of Kotlin, it was a bug that class properties with a function type weren't smart cast.

We fixed this behavior in the K2 compiler in Kotlin 2.0.0.
For example:

```kotlin
class Holder(val provider: (() -> Unit)?) {
    fun process() {
        // In Kotlin 2.0.0, if provider isn't null, then
        // provider is smart cast
        if (provider != null) {
            // The compiler knows that provider isn't null
            provider()

            // In 1.9.20, the compiler doesn't know that provider isn't 
            // null, so it triggers an error:
            // Reference has a nullable type '(() -> Unit)?', use explicit '?.invoke()' to make a function-like call instead
        }
    }
}
```

This change also applies if you overload your `invoke` operator. For example:

```kotlin
interface Provider {
    operator fun invoke()
}

interface Processor : () -> String

class Holder(val provider: Provider?, val processor: Processor?) {
    fun process() {
        if (provider != null) {
            provider() 
            // In 1.9.20, the compiler triggers an error: 
            // Reference has a nullable type 'Provider?' use explicit '?.invoke()' to make a function-like call instead
        }
    }
}
```
#### Exception handling

In Kotlin 2.0.0, we've made improvements to exception handling so that smart cast information can be passed
on to `catch` and `finally` blocks. This change makes your code safer as the compiler keeps track of whether
your object has a nullable type or not.

For example:

```kotlin
//sampleStart
fun testString() {
    var stringInput: String? = null
    // stringInput is smart cast to String type
    stringInput = ""
    try {
        // The compiler knows that stringInput isn't null
        println(stringInput.length)
        // 0

        // The compiler rejects previous smart cast information for 
        // stringInput. Now stringInput has the String? type.
        stringInput = null

        // Trigger an exception
        if (2 > 1) throw Exception()
        stringInput = ""
    } catch (exception: Exception) {
        // In Kotlin 2.0.0, the compiler knows stringInput 
        // can be null, so stringInput stays nullable.
        println(stringInput?.length)
        // null

        // In Kotlin 1.9.20, the compiler says that a safe call isn't
        // needed, but this is incorrect.
    }
}
//sampleEnd
fun main() {
    testString()
}
```
{kotlin-runnable="true" kotlin-min-compiler-version="2.0" id="kotlin-smart-casts-k2-exception-handling-guide"}

#### Increment and decrement operators

Prior to Kotlin 2.0.0, the compiler didn't understand that the type of an object can change after using
an increment or decrement operator. As the compiler couldn't accurately track the object type,
your code could lead to unresolved reference errors. In Kotlin 2.0.0, this is fixed:

```kotlin
interface Rho {
    operator fun inc(): Sigma = TODO()
}

interface Sigma : Rho {
    fun sigma() = Unit
}

interface Tau {
    fun tau() = Unit
}

fun main(input: Rho) {
    var unknownObject: Rho = input

    // Check if unknownObject inherits from the Tau interface
    if (unknownObject is Tau) {

        // Uses the overloaded inc() operator from interface Rho,
        // which smart casts the type of unknownObject to Sigma.
        ++unknownObject

        // In Kotlin 2.0.0, the compiler knows unknownObject has type
        // Sigma, so the sigma() function is called successfully.
        unknownObject.sigma()

        // In Kotlin 1.9.20, the compiler thinks unknownObject has type
        // Tau, so calling the sigma() function throws an error.

        // In Kotlin 2.0.0, the compiler knows unknownObject has type
        // Sigma, so calling the tau() function throws an error.
        unknownObject.tau()
        // Unresolved reference 'tau'

        // In Kotlin 1.9.20, the compiler mistakenly thinks that 
        // unknownObject has type Tau, so the tau() function is
        // called successfully.
    }
}
```
{kotlin-runnable="true" kotlin-min-compiler-version="2.0" id="kotlin-smart-casts-k2-increment-decrement-operators-guide" validate="false"}

### Kotlin Multiplatform

In Kotlin 2.0.0, we've made improvements in the K2 compiler related to Kotlin Multiplatform in the following areas:
* [Separation of common and platform sources during compilation](#separation-of-common-and-platform-sources-during-compilation)
* [Different visibility levels of expected and actual declarations](#different-visibility-levels-of-expected-and-actual-declarations)

#### Separation of common and platform sources during compilation

Previously, due to its design, the Kotlin compiler couldn't keep common and platform source sets separate at compile time.
This means that common code could access platform code, which resulted in different behavior between platforms. In addition,
some compiler settings and dependencies from common code were leaked into platform code.

In Kotlin 2.0.0, we redesigned the compilation scheme as part of the new Kotlin K2 compiler so that there is a strict
separation between common and platform source sets. The most noticeable change is when you use [expected and actual functions](multiplatform-expect-actual.md#expected-and-actual-functions).
Previously, it was possible for a function call in your common code to resolve to a function in platform code. For example:

<table header-style="top">
   <tr>
       <td>Common code</td>
       <td>Platform code</td>
   </tr>
   <tr>
<td>

```kotlin
fun foo(x: Any) = println("common foo")

fun exampleFunction() {
    foo(42)  
}
```

</td>
<td>

```kotlin
// JVM
fun foo(x: Int) = println("platform foo")

// JavaScript
// There is no foo() function overload
// on the JavaScript platform
```

</td>
</tr>
</table>

In this example, the common code has different behavior depending on which platform it is run on:

* On the JVM platform, calling the `foo()` function in common code results in the `foo()` function from platform code
  being called: `platform foo`

* On the JavaScript platform, calling the `foo()` function in common code results in the `foo()` function from common
  code being called: `common foo`, since there is none available in platform code.

In Kotlin 2.0.0, common code doesn't have access to platform code, so both platforms successfully resolve the `foo()`
function to the `foo()` function in common code: `common foo`

In addition to the improved consistency of behavior across platforms, we also worked hard to fix cases where there was
conflicting behavior between IntelliJ IDEA or Android Studio and the compiler. For example, if you used [expected and actual classes](multiplatform-expect-actual.md#expected-and-actual-classes):

<table header-style="top">
   <tr>
       <td>Common code</td>
       <td>Platform code</td>
   </tr>
   <tr>
<td>

```kotlin
expect class Identity {
    fun confirmIdentity(): String
}

fun common() {
    // Before Kotlin 2.0.0,
    // it triggers an IDE-only error
    Identity().confirmIdentity()
    // RESOLUTION_TO_CLASSIFIER : Expected class
    // Identity has no default constructor.
}

```

</td>
<td>

```kotlin
actual class Identity {
    actual fun confirmIdentity() = "expect class fun: jvm"
}
```

</td>
</tr>
</table>

In this example, the expected class `Identity` has no default constructor, so it can't be called successfully in common code.
Previously, only an IDE error was reported, but the code still compiled successfully on the JVM. However, now the compiler
correctly reports an error:

```kotlin
Expected class 'expect class Identity : Any' does not have default constructor
```

##### When resolution behavior doesn't change

We are still in the process of migrating to the new compilation scheme, so the resolution behavior is still the same when
you call functions that aren't within the same source set. You will notice this difference mainly when you use overloads
from a multiplatform library in your common code.

For example, if you have this library, which has two `whichFun()` functions with different signatures:

```kotlin
// Example library

// MODULE: common
fun whichFun(x: Any) = println("common function") 

// MODULE: JVM
fun whichFun(x: Int) = println("platform function")
```

If you call the `whichFun()` function in your common code, the function that has the most relevant argument type in the
library is resolved:

```kotlin
// A project that uses the example library for the JVM target

// MODULE: common
fun main(){
    whichFun(2) 
    // platform function
}
```
In comparison, if you declare the overloads for `whichFun()` within the same source set, the function from common code
is resolved because your code doesn't have access to the platform-specific version:

```kotlin
// Example library isn't used

// MODULE: common
fun whichFun(x: Any) = println("common function") 

fun main(){
    whichFun(2) 
    // common function
}

// MODULE: JVM
fun whichFun(x: Int) = println("platform function")
```

Similar to multiplatform libraries, since the `commonTest` module is in a separate source set, it also still has access
to platform-specific code. Therefore, the resolution of calls to functions in the `commonTest` module has the same behavior
as in the old compilation scheme.

In the future, these remaining cases will be more consistent with the new compilation scheme.

#### Different visibility levels of expected and actual declarations

Before Kotlin 2.0.0, if you used [expected and actual declarations](multiplatform-expect-actual.md) in your
Kotlin Multiplatform project, they had to have the same [visibility level](visibility-modifiers.md).
Kotlin 2.0.0 supports different visibility levels **only** if the actual declaration is _less_ strict than
the expected declaration. For example:

```kotlin
expect internal class Attribute // Visibility is internal
actual class Attribute          // Visibility is public by default,
                                // which is less strict
```

If you are using a [type alias](type-aliases.md) in your actual declaration, the visibility of the type **must** be less
strict. Any visibility modifiers for `actual typealias` are ignored. For example:

```kotlin
expect internal class Attribute                 // Visibility is internal
internal actual typealias Attribute = Expanded  // The internal visibility 
                                                // modifier is ignored
class Expanded                                  // Visibility is public by default,
                                                // which is less strict
```

## How to enable the Kotlin K2 compiler

From Kotlin 2.0.0, the Kotlin K2 compiler is enabled by default.

To update to the new Kotlin version, change the Kotlin version to 2.0.0 in your [Gradle](gradle-configure-project.md#apply-the-plugin)
and [Maven](maven.md#configure-and-enable-the-plugin) build scripts.

## How to enable IDE support

IntelliJ IDEA can use the new K2 compiler to analyze your code with its K2 Kotlin mode from [IntelliJ IDEA 2024.1 EAP 1](https://blog.jetbrains.com/idea/2024/01/intellij-idea-2024-1/#intellij-idea%\E2%80%99s-k2-kotlin-mode-now-in-alpha).

> The K2 Kotlin mode is in Alpha. The performance and stability of code highlighting and code completion have been improved,
> but not all IDE features are supported yet.
>
{type="warning"}

## How to roll back to the previous compiler

To use the previous compiler in Kotlin 2.0.0, either:

* in your `build.gradle.kts` file, [set your language version](gradle-compiler-options.md#example-of-setting-a-languageversion) to `1.9`

  OR
* use the following compiler option: `-language-version 1.9`

## Changes

As a result of the new frontend, the Kotlin compiler has undergone some changes. We first highlight the most noticeable 
changes affecting your code, explaining what has changed and the best practices going forward. For those changes that may
go unnoticed, we have organized them into subject areas for further reading, should you want to learn more.

### Highlighted changes

This section highlights the following changes:
* [Immediate initialization of open properties with backing fields](#immediate-initialization-of-open-properties-with-backing-fields)
* [Deprecate use of a synthetic setter on a projected receiver](#deprecate-use-of-a-synthetic-setter-on-a-projected-receiver)
* [Consistent resolution order of Kotlin properties and Java fields with the same name](#consistent-resolution-order-of-kotlin-properties-and-java-fields-with-the-same-name)
* [Improved nullability safety for Java primitive arrays](#improved-nullability-safety-for-java-primitive-arrays)

#### Immediate initialization of open properties with backing fields

**What's changed?**

In Kotlin 2.0, all `open` properties with backing fields must be immediately initialized, otherwise you'll get a compilation
error. Previously, only `open var` properties needed to be initialized right away, now this extends to `open val`s with 
backing fields as well:

```kotlin
open class Base {
    open val a: Int
    open var b: Int
    
    init {
        this.a = 1 // Error in Kotlin 2.0 that compiled successfully in earlier versions
        this.b = 1 // Error: open var must have initializer
    }
}

class Derived : Base() {
    override val a: Int = 2
    override var b = 2
}
```
This change makes the compiler behavior more predictable. Consider an example when the  `open val` property is overridden
by the `var` with a custom setter.

If the custom setter is presented, deferred initialization is confusing because it's unclear whether you want to initialize
the backing field or to invoke the setter. In case you wanted to invoke the setter, the compiler couldn't guarantee that
the setter would initialize the backing field.

**What's the best practice now?**

We encourage you to always initialize open properties with backing fields, as we believe it's a better, less error-prone
code practice.

However, if you don't want to immediately initialize a property, you can:

* Make the property `final`.
* Use a private backing property whose initialization can be deferred.

#### Deprecate use of a synthetic setter on a projected receiver

**What's changed?**

If you use the synthetic setter of a Java class to assign a type that conflicts with the class's projected type, an error is triggered.

For example, if you have a Java class named `Container` that contains `getFoo()` and `setFoo()` methods:

```java
public class Container<E> {
    public E getFoo() {
        return null;
    }
    public void setFoo(E foo) {}
}
```

And if you have the following Kotlin code, where instances of the `Container` class have projected types, using the `setFoo()` method always generates an error. However, only from Kotlin 2.0.0, does the synthetic `foo` property, trigger an error:

```kotlin
fun exampleFunction(firstContainer: Container<*>, secondContainer: Container<in Number>, sampleString: String) {
    container.setFoo(sampleString) 
    // Error since Kotlin 1.0
    
    // Synthetic setter `foo` is resolved to the `setFoo()` method
    container.foo = sampleString
    // Error since Kotlin 2.0.0

    secondContainer.setFoo(sampleString) 
    // Error since Kotlin 1.0

    // Synthetic setter `foo` is resolved to the `setFoo()` method
    secondContainer.foo = sampleString
    // Error since Kotlin 2.0.0
    
}
```

**What's the best practice now?**

If you see that this change introduces errors in your code, reconsider your type declarations. Either you don't need to 
use type projections or you need to remove the assignment in your code.

For more information, see the [corresponding issue in YouTrack](https://youtrack.jetbrains.com/issue/KT-54309).

#### Consistent resolution order of Kotlin properties and Java fields with the same name

**What's changed?**

Before Kotlin 2.0.0, if you worked with Java and Kotlin classes that inherited from each other, and contained Kotlin 
properties and Java fields with the same name, the resolution behavior of the duplicated name was inconsistent. There was
also conflicting behavior between IntelliJ IDEA and the compiler.

For example, previously if there was a Java class `Base`:

```java
public class Base {
    public String a = "a";

    public String b = "b";
}
```
And there was a Kotlin class `Derived` that inherits from the `Base` class:

```kotlin
class Derived : Base() {
    val a = "aa"

    // Declare custom get() function
    val b get() = "bb"
        }

    fun main() {
        // Resolves Derived.a
        println(a)
        // aa

        // Resolves Base.b
        println(b)
        // b
    }
}
```

`a` was resolved to the Kotlin property within the `Derived` Kotlin class. Whereas `b` was resolved to the Java field in
the `Base` Java class.

In Kotlin 2.0.0, the resolution behavior in the example is made consistent so that the Kotlin property supersedes the 
Java field of the same name. `b` now resolves to: `Derived.b`.

> Prior to Kotlin 2.0.0, if you used IntelliJ IDEA to go to the declaration or usage of `a`, the
> IntelliJ IDEA incorrectly navigated to the Java field when it should have navigated to the Kotlin > property. From Kotlin
> 2.0.0, IntelliJ IDEA correctly navigates to the same location as the
> compiler.
>
{type ="note"}

The general rule is that the subclass takes precedence. The previous example demonstrates this as the Kotlin property `a`
from the `Derived` class is resolved because `Derived` is a subclass of the `Base` Java class.

In the scenario where inheritance is reversed, so a Java class inherits from a Kotlin class, then the Java field in the 
subclass takes precedence over the Kotlin property of the same name.

For example:

<table header-style="top">
   <tr>
       <td>Kotlin</td>
       <td>Java</td>
   </tr>
   <tr>
<td>

```kotlin
open class Base {
    val a = "aa"
        }
```

</td>
<td>

```java
public class Derived extends Base {
    public String a = "a";
}
```

</td>
</tr>
</table>

In the following code:

```kotlin
    fun main() {
        // Resolves Derived.a
        println(a)
        // a
    }
}
```

In the case where a Java class contains a field and a synthetic property of the same name, the field supersedes the property. For example:

<table header-style="top">
   <tr>
       <td>Java</td>
       <td>Kotlin</td>
   </tr>
   <tr>
<td>

```java
public class JavaClass {
    public final String name;

    public String getName() {
        return name;
    }
}
```

</td>
<td>

```kotlin
fun main() {
    // Resolves Java field: name
    JavaClass.name
}
```

</td>
</tr>
</table>

**What's the best practice now?**

The resolution behavior was chosen to cause the least impact to users. Compiler warnings were introduced in Kotlin 1.8.20
to warn about changes in resolution behavior in Kotlin 2.0.0.

For more information, see the [corresponding issue in YouTrack](https://youtrack.jetbrains.com/issue/KT-55017).

#### Improved nullability safety for Java primitive arrays

**What's changed?**

Starting with Kotlin 2.0.0, the compiler correctly infers nullability of the Java primitive arrays imported to Kotlin. 
Now, it retains native nullability from the `TYPE_USE` annotations used with Java primitive arrays and emits errors when
their values are not used according to annotations.

Usually, when Java types with `@Nullable` and `@NotNull` annotations are called from Kotlin, they receive the appropriate
native nullability:

```java
interface DataService {
    @NotNull ResultContainer<@Nullable String> fetchData();
}
```
```kotlin
val dataService: DataService = ... 
dataService.fetchData() // -> ResultContainer<String?>
```

Previously however, when the Java primitive arrays were imported to Kotlin, all `TYPE_USE` annotations were lost, causing
platform nullability and possibly unsafe code:

```java
interface DataProvider {
    int @Nullable [] fetchData();
}
```

```kotlin
val dataService: DataProvider = …
dataService.fetchData() // -> IntArray .. IntArray?
// No error, even though `dataService.fetchData()` might be `null` according to annotations
// This might result in the NullPointerException
dataService.fetchData()[0]
```
Note that this issue never affected nullability annotations on the declaration itself, only the `TYPE_USE` ones.

**What's the best practice now?**

In Kotlin 2.0.0, the nullability safety of Java primitive arrays is now standard for Kotlin, so check your code for new 
warnings and errors if you use them:

* Any code that uses a `@Nullable` Java primitive array without an explicit nullability check or attempts to pass `null`
to a Java method expecting a non-nullable primitive array now fails to compile.
* Using a `@NotNull` primitive array with a nullability check now emits an "unnecessary safe call" or a "comparison with
null always false" warning.

For more information, see the [corresponding issue in YouTrack](https://youtrack.jetbrains.com/issue/KT-54521).

### Changes per subject area

These subject areas list changes that are unlikely to affect your code but provide links to the relevant YouTrack issues
for further reading.

#### Type inference

| Issue ID | Title                                                                                                      |
|----------|------------------------------------------------------------------------------------------------------------|
| KT-64189 | Incorrect type in compiled function signature of property reference if the type is Normal explicitly       |
| KT-47986 | Forbid implicit inferring a type variable into an upper bound in the builder inference context             |
| KT-59275 | K2: Require explicit type arguments for generic annotation calls in array literals                         |
| KT-53752 | Missed subtyping check for an intersection type                                                            |
| KT-59138 | Change Java type parameter based types default representation in Kotlin                                    |
| KT-57178 | Change inferred type of prefix increment to return type of getter instead of return type of inc() operator |
| KT-57609 | K2: Stop relying on the presence of @UnsafeVariance using for contravariant parameters                     |
| KT-57620 | K2: Forbid resolution to subsumed members for raw types                                                    |
| KT-64641 | K2: Properly inferred type of callable reference to a callable with extension-function parameter           |
| KT-57011 | Make real type of a destructuring variable consistent with explicit type when specified                    |
| KT-38895 | K2: Fix inconsistent behavior with integer literals overflow                                               |
| KT-54862 | Anonymous type can be exposed from anonymous function from type argument                                   |
| KT-22379 | Condition of while-loop with break can produce unsound smartcast                                           |
| KT-62507 | K2: Prohibit smart cast in common code for expect/actual top-level property                                |
| KT-65750 | Increment and plus operators that change return type must affect smart casts                               |
| KT-65349 | [LC] K2: specifying variable types explicitly breaks bound smart casts in some cases that worked in K1     |

#### Resolution

| Issue ID | Title                                                                                                                         |
|----------|-------------------------------------------------------------------------------------------------------------------------------|
| KT-58260 | Make invoke convention works consistently with expected desugaring                                                            |
| KT-62866 | K2: Change qualifier resolution behavior when companion object is preferred against static scope                              |
| KT-57750 | Report ambiguity error when resolving types and having the same-named classes star imported                                   |
| KT-63558 | K2: migrate resolution around COMPATIBILITY_WARNING                                                                           |
| KT-51194 | False negative CONFLICTING_INHERITED_MEMBERS when dependency class contained in two different versions of the same dependency |
| KT-37592 | Property invoke of a functional type with receiver is preferred over extension function invoke                                |
| KT-51666 | Qualified this: introduce/prioritize this qualified with type case                                                            |
| KT-54166 | Confirm unspecified behavior in case of FQ name conflicts in classpath                                                        |
| KT-64431 | K2: forbid using typealiases as qualifier in imports                                                                          |
| KT-56520 | K1/K2: incorrect work of resolve tower for type references with ambiguity at lower level                                      |

#### Null safety

| Issue ID | Title                                                                                       |
|----------|---------------------------------------------------------------------------------------------|
| KT-41034 | K2: Change evaluation semantics for combination of safe calls and convention operators      |
| KT-50850 | Order of supertypes defines nullability parameters of inherited functions                   |
| KT-53982 | Keep nullability when approximating local types in public signatures                        |
| KT-62998 | Forbid assignment of a nullable to a not-null Java field as a selector of unsafe assignment |
| KT-63209 | Report missing errors for error-level nullable arguments of warning-level Java types        |

#### Properties

| Issue ID | Title                                                                                                         |
|----------|---------------------------------------------------------------------------------------------------------------|
| KT-58589 | Deprecate missed MUST_BE_INITIALIZED when no primary constructor is presented or when class is local          |
| KT-64295 | Forbid recursive resolve in case of potential invoke calls on properties                                      |
| KT-57290 | Deprecate smart cast on base class property from invisible derived class if base class is from another module |
| KT-62661 | K2: Missed OPT_IN_USAGE_ERROR for data class properties                                                       |

#### Annotations

| Issue ID | Title                                                                                                  |
|----------|--------------------------------------------------------------------------------------------------------|
| KT-58723 | Forbid annotating statements with an annotation if it has no EXPRESSION target                         |
| KT-49930 | Ignore parentheses expression during \`REPEATED_ANNOTATION\` checking                                  |
| KT-57422 | K2: Prohibit use-site 'get' targeted annotations on property getters                                   |
| KT-46483 | Prohibit annotation on type parameter in where clause                                                  |
| KT-64299 | Companion scope is ignored for resolution of annotations on companion object                           |
| KT-64654 | K2: Introduced ambiguity between user and compiler-required annotations                                |
| KT-64527 | Annotations on enum values shouldn't be copied to enum value classes                                   |
| KT-63389 | K2: \`WRONG_ANNOTATION_TARGET\` is reported on incompatible annotations of a type wrapped into \`()?\` |
| KT-63388 | K2: \`WRONG_ANNOTATION_TARGET\` is reported on catch parameter type's annotations                      |

#### Generics

| Issue ID | Title                                                                                                                                                 |
|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| KT-57600 | Forbid overriding of Java method with raw-typed parameter with generic typed parameter                                                                |
| KT-54663 | Forbid passing possibly nullable type parameter to \`in\` projected DNN parameter                                                                     |
| KT-54066 | Deprecate upper bound violation in typealias constructors                                                                                             |
| KT-49404 | Fix type unsoundness for contravariant captured type based on Java class                                                                              |
| KT-61718 | Forbid unsound code with self upper bounds and captured types                                                                                         |
| KT-61749 | Forbid unsound bound violation in generic inner class of generic outer class                                                                          |
| KT-62923 | K2: Introduce PROJECTION_IN_IMMEDIATE_ARGUMENT_TO_SUPERTYPE for projections of outer super types of inner class                                       |
| KT-63243 | Report MANY_IMPL_MEMBER_NOT_IMPLEMENTED when inheriting from collection of primitives with an extra specialized implementation from another supertype |
| KT-60305 | K2: Prohibit constructor call and inheritance on type alias that has variance modifiers in expanded type                                              |
| KT-64965 | Fix type hole caused by improper handling of captured types with self-upper bounds                                                                    |
| KT-64966 | Forbid generic delegating constructor calls with wrong type for generic parameter                                                                     |
| KT-65712 | Report missing upper bound violation when upper bound is captured type                                                                                |

#### Java interoperability

| Issue ID | Title                                                                                                      |
|----------|------------------------------------------------------------------------------------------------------------|
| KT-53061 | Forbid Java and Kotlin classes with the same FQ name in sources                                            |
| KT-49882 | Classes inherited from Java collections have inconsistent behavior depending on order of supertypes        |
| KT-66324 | K2: unspecified behavior in case of Java class inheritance from a Kotlin private class                     |
| KT-66220 | Passing java vararg method to inline function leads to array of arrays in runtime instead of just an array |
| KT-66204 | Allow to override internal members in K-J-K hierarchy                                                      |

#### Companion object

| Issue ID | Title                                                                    |
|----------|--------------------------------------------------------------------------|
| KT-54316 | Out-of-call reference to companion object's member has invalid signature |
| KT-47313 | Change (V)::foo reference resolution when V has a companion              |

#### Control flow

| Issue ID | Title                                                                                      |
|----------|--------------------------------------------------------------------------------------------|
| KT-56408 | Inconsistent rules of CFA in class initialization block between K1 and K2                  |
| KT-57871 | K1/K2 inconsistency on if-conditional without else-branch in parenthesis                   |
| KT-42995 | False negative "VAL_REASSIGNMENT" in try/catch block with initialization in scope function |
| KT-65724 | Propagate data flow information from try block to catch and finally blocks                 |

#### Functional (SAM) interfaces

| Issue ID | Title                                                                                                           |
|----------|-----------------------------------------------------------------------------------------------------------------|
| KT-52628 | Deprecate SAM constructor usages which require OptIn without annotation                                         |
| KT-57014 | Prohibit returning values with incorrect nullability from lambda for SAM constructor of JDK function interfaces |
| KT-64342 | SAM conversion of parameter types of callable references leads to CCE                                           |

#### Enum classes

| Issue ID | Title                                                                                        |
|----------|----------------------------------------------------------------------------------------------|
| KT-57608 | Prohibit access to the companion object of enum class during initialization of enum entry    |
| KT-34372 | Report missed error for virtual inline method in enum classes                                |
| KT-52802 | Report ambiguity resolving between property/field and enum entry                             |
| KT-47310 | Change qualifier resolution behavior when companion property is preferred against enum entry |

#### Visibility

| Issue ID | Title                                                                                                                          |
|----------|--------------------------------------------------------------------------------------------------------------------------------|
| KT-55179 | False negative PRIVATE_CLASS_MEMBER_FROM_INLINE on calling private class companion object member from internal inline function |
| KT-58042 | Make synthetic property invisible if equivalent getter is invisible even when overridden declaration is visible                |
| KT-64255 | Forbid accessing internal setter from a derived class in another module                                                        |
| KT-33917 | Prohibit to expose anonymous types from private inline functions                                                               |
| KT-54997 | Forbid implicit non-public-API accesses from public-API inline function                                                        |
| KT-56310 | Smart casts should not affect visibility of protected members                                                                  |
| KT-65494 | Forbid access to overlooked private operator functions from public inline function                                             |
| KT-65004 | K1: Setter of var, which overrides protected val, is generates as public                                                       |
| KT-64972 | Forbid overriding by private members in link-time for Kotlin/Native                                                            |

#### Miscellaneous

| Issue ID | Title                                                                                                      |
|----------|------------------------------------------------------------------------------------------------------------|
| KT-49015 | Qualified this: change behavior in case of potential label conflicts                                       |
| KT-56545 | Fix incorrect functions mangling in JVM backend in case of accidental clashing overload in a Java subclass |
| KT-62019 | [LC issue] Prohibit suspend-marked anonymous function declarations in statement positions                  |
| KT-55111 | OptIn: forbid constructor calls with default arguments under marker                                        |
| KT-61182 | Unit conversion is accidentally allowed to be used for expressions on variables + invoke resolution        |
| KT-55199 | Forbid promoting callable references with adaptations to KFunction                                         |
| KT-65776 | [LC] K2 breaks \`false && ...\` and \`false                                                                || ...\`                                                       |
| KT-65682 | [LC] Deprecate \`header\`/\`impl\` keywords                                                                |

### Compatibility with Kotlin releases

The following Kotlin releases have support for the new K2 compiler:

| Kotlin release | Stability level |
|----------------|-----------------|
| 2.0.0          | Stable          |
| 1.9.20–1.9.23  | Beta            |
| 1.9.0–1.9.10   | JVM is Beta     |
| 1.7.0–1.8.22   | Alpha           |

### Compiler plugins support

Currently, the Kotlin K2 compiler supports the following Kotlin compiler plugins:

* [`all-open`](all-open-plugin.md)
* [AtomicFU](https://github.com/Kotlin/kotlinx-atomicfu)
* [`jvm-abi-gen`](https://github.com/JetBrains/kotlin/tree/master/plugins/jvm-abi-gen)
* [`js-plain-objects`](https://github.com/JetBrains/kotlin/tree/master/plugins/js-plain-objects)
* [kapt](whatsnew1920.md#preview-kapt-compiler-plugin-with-k2)
* [Lombok](lombok.md)
* [`no-arg`](no-arg-plugin.md)
* [Parcelize](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.plugin.parcelize)
* [SAM with receiver](sam-with-receiver-plugin.md)
* [serialization](serialization.md)

In addition, the Kotlin K2 compiler supports:
* the [Jetpack Compose](https://developer.android.com/jetpack/compose) 1.5.0 compiler plugin and later versions.
* the [Kotlin Symbol Processing (KSP) plugin](ksp-overview.md)
  since [KSP2](https://android-developers.googleblog.com/2023/12/ksp2-preview-kotlin-k2-standalone.html).

> If you use any additional compiler plugins, check their documentation to see if they are compatible with K2.
>
{type="tip"}

### Performance

TBD

### Leave your feedback on the new K2 compiler

We would appreciate any feedback you may have!

* Provide your feedback directly to K2 developers on Kotlin
  Slack – [get an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up?_gl=1*ju6cbn*_ga*MTA3MTk5NDkzMC4xNjQ2MDY3MDU4*_ga_9J976DJZ68*MTY1ODMzNzA3OS4xMDAuMS4xNjU4MzQwODEwLjYw)
  and join the [#k2-early-adopters](https://kotlinlang.slack.com/archives/C03PK0PE257) channel.
* Report any problems you face migrating to the new K2 compiler
  in [our issue tracker](https://youtrack.jetbrains.com/newIssue?project=KT&summary=K2+release+migration+issue&description=Describe+the+problem+you+encountered+here.&c=tag+k2-release-migration).
* [Enable the Send usage statistics option](https://www.jetbrains.com/help/idea/settings-usage-statistics.html) to
  allow JetBrains to collect anonymous data about K2 usage.