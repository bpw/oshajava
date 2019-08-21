**Updates, 20 August 2019:** Due to Bitbucket's pending complete
termination of Mercurial repository hosting, this project, formerly
archived at <https://bitbucket.org/bpw/oshajava>, has moved to
<https://gitlab.com/atlas-lab/oshajava>. The former wiki has been rolled
into this file.

OshaJava
========

OshaJava is a system of annotations to specify what methods in a Java
program are allowed to communicate via shared memory when run in
separate threads, and a runtime that dynamically checks conformance to
the specification, throwing an exception if the specification is
violated. Inter-thread code communication (when a value written in some
method in one thread is later read in another method in a different
thread) is typically implicit and programmers have no direct way to
check or control it. OshaJava makes such code communication explicit and
lets a programmer check that only the communication they expect actually
occurs at run time. It is complementary to many data-centric approaches
for preventing or detecting synchronization errors in shared memory,
such as race detection, atomicity checking, or sharing specifications.

Our OOPSLA 2010 paper
[Composable Specifications for Structured Shared-Memory Communication](https://bitbucket.org/bpw/oshajava/downloads/osha-oopsla2010.pdf)
describes the specification and checking system in detail and evaluates
our OshaJava implementation. Some improvements have been made to the
tool since the paper was published.

Quick Start
-----------

Download the latest stable version at
<http://gitlab.com/bpw/oshajava> or check out a copy of the
code (tip is usually stable here).

-   Build: `ant; source setup.sh`
-   Compile programs: `oshajavac *.java`
-   Run with checking:
    `oshajava [oshajava options] [-javaOptions [java options]] -- Program [program args]`

Documentation
-------------

-   For more detail on using the OshaJava tools, see [Tools](#OshaJava-Tools).
-   For an explanation of how to write OshaJava specs, see
    [Specifications](#OshaJava-Specifications).
-   For formal discussion of the specification and checking system, see
    our OOPSLA 2010 paper,
    [Composable Specifications for Structured Shared-Memory Communication](https://bitbucket.org/bpw/oshajava/downloads/osha-oopsla2010.pdf).

Authors
-------

The OshaJava implementation was developed by
[Benjamin P. Wood](http://www.cs.washington.edu/homes/bpw/) and
[Adrian Sampson](http://www.cs.washington.edu/homes/asampson/) as part
of the
[Organized Sharing research project](http://sampa.cs.washington.edu/sampa/Organized_Sharing_(OSHA))
with [Luis Ceze](http://www.cs.washington.edu/homes/luisceze/) and
[Dan Grossman](http://www.cs.washington.edu/homes/djg/) in the
[Sampa research group](http://sampa.cs.washington.edu) at the
[University of Washington](http://www.cs.washington.edu).

License
-------

All code in the OshaJava distribution is [licensed](LICENSE.txt) under BSD-style
licenses.

-   Everything except the `libs` directory comprises the core OshaJava
    distribution and is licensed under a BSD-style license in the file [LICENSE.txt](LICENSE.txt).
-   The OshaJava distribution includes two third-party libraries
    (licensed under BSD-style licenses) in the `libs` directory. To
    avoid instrumentation reentrancy issues in user programs that also
    use these utilities, OshaJava includes renamed versions of these
    libraries with package names prefixed `oshajava.support.`.
    -   Acme Utils (in `libs/acmeutils`) from the
        [RoadRunner](http://www.cs.williams.edu/~freund/rr/)
        distribution
    -   [ASM](http://asm.ow2.org) (in `libs/asm-3.2`)


OshaJava Tools
==============

This page describes how to use the OshaJava tools. See also how to write
[Specifications](Specifications).

Build
-----

To build, run `ant` in the top-level directory then, in any shell where
you want to use the OshaJava tools, source the `setup.sh` file to set up
a few environment variables and put the four OshaJava tools on your
`PATH`.

Compile Annotated Programs
--------------------------

With `build/oshajava.jar` on the classpath, compile all files in your
project:

    javac -cp .:path/to/distribution/build/oshajava.jar *.java

Alternately, once you\'ve sourced `setup.sh`, you have an `oshajavac`
utility available on your `PATH`. Usage is identical to javac, but
without needing to add oshajava.jar to the classpath.

    oshajavac *.java

**Note:** The OshaJava annotation processor will only see annotation in
files that are explicitly passed as arguments to the `javac` compiler.
Implicit dependencies will *not* be subject to annotation processing.

In addition to generating the usual `.class` files, the OshaJava
compilation stage reads your annotations and generates several
` .om{i,m,s} ` files next to the `.class` files. These OshaJava files
hold information needed by the `oshajava` runtime to check that
executions of your program conform to the specification expressed by
your annotations. The runtime will warn you if some of these files are
missing.

### Compilation Options

-   -Aoshajava.annotation.default=inline: By default, all methods that
    are not annotated (directly or indirectly) with a communication
    specification receive the `@NonComm` annotation, meaning they are
    not allowed to communicate at all. This option allows you to set
    this program-wide default to either inline or noncomm.
-   -Aoshajava.verbose: Print extra debugging information in the
    compiler.

### Examine Compiled Specifications

The `oshamodpp` tool lets you pretty print compiled module
specifications on disk. Usage:

    oshamodpp Module.oms

The tool works on `.oms` files (compiled module specs), `.omi` files
(incremental storage of module specs for multistage compilation), `.omm`
files (these map every method in a class to the module to which it
belongs), and any other file that happens to be a single serialized Java
object.

### Clean Compiled Specification Files

The `oshamodrm` tool deletes all ` .om{i,m,s} ` files in the directory
hierarchies rooted at its arguments. Given no argument it deletes all
such files under the current directory.

Run Programs with Communication Checking Enabled
------------------------------------------------

All of the instrumentation performed by OshaJava to track and check
communication in a program is done by a bytecode instrumentor that runs
at class-load time in the JVM. The OshaJava annotation processor does
not transform your program in any way, meaning you can compile your
program once with the OshaJava annotation processor enabled and then run
it normally with `java` or with communication checking enabled with
`oshajava`.

To run your program with specification checking enabled, use `oshajava`:

    oshajava [oshajava options] [-javaOptions [java options]] -- Program [program args]

For a complete list of runtime options, run `oshajava -help`.


OshaJava Specifications
=======================

For now, see our OOPSLA 2010 paper,
[Composable Specifications for Structured Shared-Memory Communication](https://bitbucket.org/bpw/oshajava/downloads/osha-oopsla2010.pdf).
However, several improvements to the tool and the annotation system have
been made since publication, making it easier to write the same
specification using fewer annotations.

Modules: Delimiting Layered Communication Abstractions
------------------------------------------------------

When one thread writes a value to a memory location and a second thread
later reads that value from that location, we say that the two threads
communicate. What this means in terms of methods communicating is a
little more subtel. Consider this producer-consumer pipeline
implementation backed by a bounded buffer, where many producer threads
call `produce` and many consumer threads call `consume`.

pipeline/ItemProcessingPipeline.java:

    #!java
    package pipeline;
    import buffer.BoundedBuffer;
    class ItemProcessingPipeline {
        BoundedBuffer pipe = new BoundedBuffer();

        // Called by producer threads
        void produce() {
            ...;  pipe.enqueue(...);  ...
        }

        // Called by consumer threads
        void consume() {
            ...;  ... = pipe.dequeue();  ...
        }
    }

buffer/BoundedBuffer.java:

    #!java
    package buffer;
    public class BoundedBuffer {
        Item[] buffer = new Item[10];
        int size = 0;
        ...

        public synchronized void enqueue(Item i) {
            while (size == buffer.length) wait();
            buffer[...] = i;
            size++;
            notifyAll();
        }

        public synchronized Item dequeue() {
            while (size == 0) wait();
            size--;
            notifyAll();
            return buffer[...];
        }
    }

When writes by one thread in `enqueue` communicate to reads in another
thread in `dequeue`, it makes sense to say that `enqueue` is
communicating to `dequeue`. However, at the pipeline level it also makes
sense to say that `produce` communicates to `consume`, because `produce`
called `enqueue`, which communicated to `dequeue`, called by `consume`.
We clearly think about this communication in terms of layers of
abstraction, which are visible when we compare the call stacks of the
two threads from the above example at the time of their communicating
access:

    Thread 1:     | Thread 2:
    --------------|---------------
    produce ->    | consume ->      }-- pipeline
      enqueue ->  |   dequeue ->    }-- buffer
        write     |      read       }-- memory

-   At the bottom level, a memory write operation communicates to a
    memory read operation in another thread.
-   At the next level, the `enqueue` method, which performs the write
    operation, communicates to the `dequeue` method, which performs the
    read operation.
-   At the highest level of abstraction, the `produce` method, which
    called the `enqueue` method, communicates to the `consume` method,
    which called the `dequeue` method.

We solidify this intuition for layered abstractions with modules.
Communication modules divide a program up into sets of methods that
communicate in a meaningful way. Communication between methods in a
module forms a layer of communication abstraction. The pipeline example
above has two obvious modules: the pipeline and the buffer. Memory
itself can be considered a lowest-level module, containing communicating
write and read operations. When a pair of call stacks of communicating
operations can be broken down into these layers by module, we say they
have *equivalent segmentations*. (See the paper for a precise
definition.)

### Module Annotations

By default, every Java package `p` has a module `p.Default` and every
method in package `p` belongs to this module. A specification may assign
invidividual methods or sets of methods to other modules arbitrarily
using the `@Module` annotation.

To assign method `foo` to module `bar.Qux`, we annotate it as follows:

    #!java

    @Module("bar.Qux")
    void foo() { ... }

Important things to remember about module annotations:

-   **Module names are always fully qualified.** Using `@Module("Qux")`
    refers to the module `Qux` in the top-level (unnamed) package even
    if we\'re using the annotation in the `bar` package. We have no
    equivalent of the `import` construct in Java proper.
-   **Modules are created \"on demand.\"** There is no single
    declaration of a module. By virtue of `foo` being declared a member
    of module `bar.Qux`, `bar.Qux` will be created if it does not
    already exist. (This is largely to simplify the compilation
    process.)

### Cascading Module Membership

Often, we want all (or most) of the methods in a given class or package
to belong to the same non-default module. To avoid annotating every
single method with `@Module`, both classes and packages can also take
`@Module` annotations. When a class or package is annotated with a
`@Module` annotation, all of its members (and their members, and so on)
will belong to this module as well unless they are annotated otherwise.
For example, the following class is annotated with `@Module` annotations
and each method is commented with the module it belongs to as a result.

    #!java

    @Module("A")
    class C {
        void f() { ... }  // belongs to module A
        
        @Module("B")
        void g() { ... }  // belongs to module B

        class D {
            void h() { ... }  // belongs to module A
        }

        @Module("B")
        class E {
            void i() { ... }  // belongs to module B
        }
    }

Packages can also be annotated by placing the annotation in a file
`package-info.java` in the package source location:

    #!java
    @Module("A")
    package p;
    import oshajava.annotation.Module;

When is Communication Allowed?
------------------------------

If the call stacks of the two threads at the times of their
communicating operations do not have equivalent segmentations, then the
communication is summarily disallowed. If they do have equivalent
segmentations, then from the callee-most end of the two call stacks we
check each layer: if every writer method in the layer is allowed to
communicate to every reader method in the layer, then the communication
is allowed in this layer. If communication is allowed in all layers,
then it is legal, otherwise it is not.

The next section describes how module-internal communication
specifications are declared. The following section describes a
modification of the simple checking policy above to allow for
encapsulated (hidden) communication.

Module-Internal Communication Specifications
--------------------------------------------

Within a communication module, we define which pairs of methods are
allowed to communicate.

### Groups

Rather than specifiy every pair of methods that may communicate, we use
*communication groups*. A communication group is a set of writer methods
and a set of reader methods (all from the same module), allowing any
method in the writer set to communicate to any method in the reader set.
Groups (as opposed to lists of pairs) avoid worst-case quadratic size
specifications for sets of methods many of which communicate with each
other and attach an intuitive name to a set of communicating methods.

Unlike modules, groups must be declared using a `@Group(id="GroupId")`.
Group names are *not* qualified; they belong to the module in which they
are declared. In other words, a `@Group` annotation on an element
declares a group that belongs to whatever module is attached to that
element (by cascading module annotations\...). Due to the limits of Java
annotations, `@Group`s must be declared on some element, typically a
class declaration.

### Group Memberships

Group memberships are declared in a distributed fashion: each method is
annotated with the groups in which it is a reader (`@Reader({...})`) and
the groups in which it is a writer (`@Writer({...})`). For example, we
might annotate the bounded buffer methods from the code above as
follows:

    #!java
    package buffer;
    @Group(id="BufferAndSize")
    public class BoundedBuffer {
        @Writer("BufferAndSize")
        @Reader("BufferAndSize")
        public synchronized void enqueue(Item i) {
            while (size == buffer.length) wait();
            buffer[...] = i;
            size++;
            notifyAll();
        }

        @Writer("BufferAndSize")
        @Reader("BufferAndSize")
        public synchronized Item dequeue() {
            while (size == 0) wait();
            size--;
            notifyAll();
            return buffer[...];
        }
    }

The `BufferAndSize` group belongs to the module `buffer.Default` (recall
that by default each package is a module). Both `enqueue` and `dequeue`
are made writers and readers in the group because `enqueue` communicates
to both itself and `dequeue`, and `dequeue` communicates to both itself
and `enqueue` (note the `size` field updates).

Convenience:

-   When these sets are singleton sets, the set delimiter braces
    (` {} `) may be elided, as in the example above.
-   Read-only methods and write-only methods may use just a `@Reader` or
    just a `@Writer` annotation. Behavior is as expected.

### Inline Methods

Some methods perform communication only on behalf of their callers.
(Consider `System.arrayCopy(...)`.) Rather than complicating our simple
layered model to specify all the methods such a method may communicate
with (potentially scattered across many modules), we treat it as though
it were inlined into its caller. (We use \"inlined\" in the sense of the
common compiler transformation.) For the purposes of segmenting two
communicating call stacks, we essentially remove inlined methods from
the stacks.

Even methods at the root of a call stack may be inlined; they simply
disappear form the stack. In addition, the empty call stack is always
allowed to communicate to the empty call stack (they have equivalent
segmentations), giving us the useful property that the specification in
which every method is inlined allows all communication. This makes a
good starting point for incremental development of a spec and makes it
easy to integrate unannotated libraries by setting inlined as the
default method specification.

To mark a method as inlined, use the `@Inline` annotation instead of
`@Reader` or `@Writer`.

### Non-Communicating Methods

Methods that should not communicate at all can be declared with empty
reader and writer memberships: `@Reader({}) @Writer({})`. The `@NonComm`
annotation is syntactic sugar for this. `@NonComm` is a useful default
when trying to derive a precise specification as it avoids
unintentionally inlining methods that should have their communication
restricted.

### Cascading Method Annotations

Like module membership annotations, method communication annotations may
be applied to packages and classes, setting the default annotation for
member methods, with one minor nuance. Both `@Inline` and `@NonComm`
complete override a cascading annotation of any kind. However, if a
`@Reader("A")` is cascading, note that annotating a method as just
`@Writer("B")` results in the method having the effective annotation of
`@Writer("B")  @Reader("A")`. To override the cascading `@Reader("A")`
and achieve the effective annotation `@Writer("B")  @Reader({})`, the
method must also be explicitly annotated `@Reader({})`.

Module Communication Interfaces
-------------------------------

When describing the layering of communication abstractions in the
producers-consumers program above, we only considered communication in
the \"logical\" direction from `enqueue` to `dequeue` and from `produce`
to `consume`. However, it turns out that, among the other permutations
we specified with the group memberships described above, `dequeue` may
communicate to `enqueue` via the the `size` field of the bounded buffer.
This communication makes sense in the bounded buffer, but it is really
just an internal implementation detail that is not part of the external
buffer abstraction. As such, this kind of communication should be
*encapsulated* by the bounded buffer, meaning that callers of `dequeue`
and `enqueue` from a different layer should not appear to communicate
because of it.

To provide communication encapsulation, we use *module communication
interfaces*. A module\'s communication interface describes those pairs
of methods whose communication is visible (and meaningful) to callers
outside the module. When the top (caller-most) pair of methods in a
layer is in its module\'s interface, the communication is visible to
higher layers. Otherwise, it is encapsulated, and the callers of these
methods need not be allowed to communicate.

For the bounded buffer, we only expose communication from `enqueue` to
`dequeue`, meaning the following communication is encapsulated by the
buffer layer since communication from `dequeue` to `enqueue` is not in
the buffer module\'s interface:

    Thread 1:     | Thread 2:
    --------------|---------------
    consume ->    | produce ->      
    --------------|-------------------- encapsulation boundary
      dequeue ->  |   enqueue ->    }-- buffer
        write     |      read       }-- memory

### Interface Annotations

Module interfaces are declared by a set of *interface groups*; methods
declare membership in interface groups with their writer and reader
membership annotations. (In `@Writer` and `@Reader` sets, both regular
communication groups and interface groups may appear.) Here is the fully
annotated producers-consumers program:

pipeline/ItemProcessingPipeline.java:

    #!java
    package pipeline;
    import buffer.BoundedBuffer;
    @Group(id="Pipe")
    class ItemProcessingPipeline {
        BoundedBuffer pipe = new BoundedBuffer();

        // Called by producer threads
        @Writer("Pipe")
        void produce() {
            ...;  pipe.enqueue(...);  ...
        }

        // Called by consumer threads
        @Reader("Pipe")
        void consume() {
            ...;  ... = pipe.dequeue();  ...
        }
    }

buffer/BoundedBuffer.java:

    #!java
    package buffer;
    @Group(id="BufferAndSize")
    @InterfaceGroup(id="BufferInterface")
    public class BoundedBuffer {
        Item[] buffer = new Item[10];
        int size = 0;
        ...

        @Writer({"BufferAndSize", "BufferInterface"})
        @Reader("BufferAndSize")
        public synchronized void enqueue(Item i) {
            while (size == buffer.length) wait();
            buffer[...] = i;
            size++;
            notifyAll();
        }

        @Writer("BufferAndSize")
        @Reader({"BufferAndSize", "BufferInterface"})
        public synchronized Item dequeue() {
            while (size == 0) wait();
            size--;
            notifyAll();
            return buffer[...];
        }
    }

Specification Checking with Encapsulation
-----------------------------------------

We modify the checking algorithm slightly to support encapsulation.
Communication between two operations is allowed if it is allowed by our
previous definition or if:

1.  The call stacks at the two communicating operations have equivalent
    segmentations up to some layer that encapsulates the communication;
    *AND*
2.  The internal communication within each layer up to (and including)
    the encapsulating layer is allowed.

Returning to the running example, the following communication is allowed
by the specification above, even though the pipeline module does not
allow `consume` to communicate to `produce`.

    Thread 1:     | Thread 2:
    --------------|---------------
    consume ->    | produce ->      
    --------------|-------------------- encapsulation boundary
      dequeue ->  |   enqueue ->    }-- buffer
        write     |      read       }-- memory

Note that in this example, it happens that the two stacks have fully
equivalent segmentations, even beyond the ecnapsulating layer (consume
and produce do belong to the same module), but in general this need not
be the case, since checking stops at the encapsulation boundary.

Known Limitations and Annoyances
--------------------------------

### Anonymous Classes and Named Classes Declared in Methods

Annotations on inner classes declared within methods (both anonymous and
named classes) will *not* be processed. This is a limitation of the Java
annotation processing framework.

### Multiple Group Declarations

Since only one annotation of a given type is allowed, it is necessary to
use an extra annotation when declaring more than one group or more than
one interface group on a given source element:

    #!java
    @Groups(comm={@Group("A"), @Group("B")}, intfc={@InterfaceGroup("C"), @InterfaceGroup("D")})
    class E { ... }
