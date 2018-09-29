/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;

import com.typesafe.config.impl.ConfigImplUtil;

/**
 * All exceptions thrown by the library are subclasses of
 * <code>ConfigException</code>.
 */
@SerialVersionUID(1L)
abstract class ConfigException(origin: ConfigOrigin, message: String, cause: Throwable) extends RuntimeException(origin.description() + ": " + message, cause) with Serializable {

    @transient final private val _origin: ConfigOrigin = origin

    protected def this(origin: ConfigOrigin, message: String) = {
        this(origin.description() + ": " + message, null)
    }

    // TODO(gabro)
    // protected def this(message: String, cause: Throwable) = {
    //     super(message, cause);
    //     this._origin = null;
    // }

    protected def this(message: String) = {
        this(message, null);
    }

    /**
     * Returns an "origin" (such as a filename and line number) for the
     * exception, or null if none is available. If there's no sensible origin
     * for a given exception, or the kind of exception doesn't meaningfully
     * relate to a particular origin file, this returns null. Never assume this
     * will return non-null, it can always return null.
     *
     * @return origin of the problem, or null if unknown/inapplicable
     */
    def origin(): ConfigOrigin = origin

    // we customize serialization because ConfigOrigin isn't
    // serializable and we don't want it to be (don't want to
    // support it)
    @throws(classOf[IOException])
    def writeObject(out: java.io.ObjectOutputStream): Unit = {
        out.defaultWriteObject();
        ConfigImplUtil.writeOrigin(out, origin);
    }

    @throws(classOf[IOException])
    @throws(classOf[ClassNotFoundException])
    private def readObject(in: java.io.ObjectInputStream): Unit = {
        in.defaultReadObject();
        val origin: ConfigOrigin = ConfigImplUtil.readOrigin(in);
        setOriginField(this, classOf[ConfigException], origin);
    }

}

object ConfigException {

    // For deserialization - uses reflection to set the final origin field on the object
    @throws(classOf[IOException])
    private def setOriginField[T](hasOriginField: T, clazz: Class[T], origin: ConfigOrigin): Unit = {
        // circumvent "final"
        var f: Field = null
        try {
            f = clazz.getDeclaredField("origin"); // TODO(gabro): this uses reflection
        } catch {
            case e: NoSuchFieldException =>
                throw new IOException(clazz.getSimpleName() + " has no origin field?", e);
            case e: SecurityException =>
                throw new IOException("unable to fill out origin field in " + clazz.getSimpleName(), e);
        }
        f.setAccessible(true);
        try {
            f.set(hasOriginField, origin);
        } catch {
            case e: IllegalArgumentException =>
                throw new IOException("unable to set origin field", e);
            case e: IllegalAccessException =>
                throw new IOException("unable to set origin field", e);
        }
    }

    /**
     * Exception indicating that the type of a value does not match the type you
     * requested.
     *
     */
    @SerialVersionUID(1L)
    class WrongType(origin: ConfigOrigin, path: String, expected: String, actual: String, cause: Throwable)
        extends ConfigException(origin, path + " has type " + actual + " rather than " + expected, cause) {

        def this(origin: ConfigOrigin, path: String, expected: String, actual: String) =
            this(origin, path, expected, actual, null);

        // TODO(gabro)
        // def this(origin: ConfigOrigin, message: String, cause: Throwable) =
        //     super(origin, message, cause);

        // TODO(gabro)
        // def this(origin: ConfigOrigin, message: String) =
        //     super(origin, message, null);
    }

    /**
     * Exception indicates that the setting was never set to anything, not even
     * null.
     */
    @SerialVersionUID(1L)
    class Missing(path: String, cause: Throwable)
        extends ConfigException("No configuration setting found for key '" + path + "'", cause) {

        def this(origin: ConfigOrigin, path: String) =
            this(origin, "No configuration setting found for key '" + path + "'", null);

        def this(path: String) =
            this(path, null);

        // TODO(gabro)
        // protected def this(origin: ConfigOrigin, message: String, cause: Throwable) =
        //     super(origin, message, cause);

    }

    /**
     * Exception indicates that the setting was treated as missing because it
     * was set to null.
     */
    @SerialVersionUID(1L)
    class Null(origin: ConfigOrigin, path: String, expected: String, cause: Throwable)
        extends Missing(origin, Null.makeMessage(path, expected), cause) {

        def this(origin: ConfigOrigin, path: String, expected: String) =
            this(origin, path, expected, null);
    }
    object Null {
        private def makeMessage(path: String, expected: String): String =
            if (expected != null) {
                "Configuration key '" + path + "' is set to null but expected " + expected;
            } else {
                "Configuration key '" + path + "' is null";
            }
    }

    /**
     * Exception indicating that a value was messed up, for example you may have
     * asked for a duration and the value can't be sensibly parsed as a
     * duration.
     *
     */
    @SerialVersionUID(1L)
    class BadValue(origin: ConfigOrigin, path: String, message: String, cause: Throwable)
        extends ConfigException(origin, "Invalid value at '" + path + "': " + message, cause) {

        def this(origin: ConfigOrigin, path: String, message: String) =
            this(origin, path, message, null);

        // TODO(gabro)
        // def this(path: String, message: String, cause: Throwable) =
        //     super("Invalid value at '" + path + "': " + message, cause);

        def this(path: String, message: String) =
            this(path, message, null);
    }

    /**
     * Exception indicating that a path expression was invalid. Try putting
     * double quotes around path elements that contain "special" characters.
     *
     */
    @SerialVersionUID(1L)
    class BadPath(origin: ConfigOrigin, path: String, message: String, cause: Throwable)
        extends ConfigException(origin, path != null ? ("Invalid path '" + path + "': " + message): message, cause) {

        def this(origin: ConfigOrigin, path: String, message: String) =
            this(origin, path, message, null);

        // TODO(gabro)
        // def this(path: String, message: String, cause: Throwable) =
        //     super(path != null ? ("Invalid path '" + path + "': " + message)
        //             : message, cause);

        def this(path: String, message: String) =
            this(path, message, null);

        def this(origin: ConfigOrigin, message: String) =
            this(origin, null, message);
    }

    /**
     * Exception indicating that there's a bug in something (possibly the
     * library itself) or the runtime environment is broken. This exception
     * should never be handled; instead, something should be fixed to keep the
     * exception from occurring. This exception can be thrown by any method in
     * the library.
     */
    @SerialVersionUID(1L)
    class BugOrBroken(message: String, cause: Throwable) extends ConfigException(message, cause) {

        def this(message: String) =
            this(message, null);
    }

    /**
     * Exception indicating that there was an IO error.
     *
     */
    @SerialVersionUID(1L)
    class IO(origin: ConfigOrigin, message: String, cause: Throwable)
        extends ConfigException(origin, message, cause) {

        def this(origin: ConfigOrigin, message: String) =
            this(origin, message, null);
    }

    /**
     * Exception indicating that there was a parse error.
     *
     */
    @SerialVersionUID(1L)
    class Parse(origin: ConfigOrigin, message: String, cause: Throwable) extends ConfigException(origin, message, cause) {

        def this(origin: ConfigOrigin, message: String) =
            this(origin, message, null);
    }

    /**
     * Exception indicating that a substitution did not resolve to anything.
     * Thrown by {@link Config#resolve}.
     */
    @SerialVersionUID(1L)
    class UnresolvedSubstitution(origin: ConfigOrigin, detail: String, cause: Throwable) extends Parse(origin, "Could not resolve substitution to a value: " + detail, cause) {

        def this(origin: ConfigOrigin, detail: String) =
            this(origin, detail, null);
    }

    /**
     * Exception indicating that you tried to use a function that requires
     * substitutions to be resolved, but substitutions have not been resolved
     * (that is, {@link Config#resolve} was not called). This is always a bug in
     * either application code or the library; it's wrong to write a handler for
     * this exception because you should be able to fix the code to avoid it by
     * adding calls to {@link Config#resolve}.
     */
    @SerialVersionUID(1L)
    class NotResolved(message: String, cause: Throwable) extends BugOrBroken(message, cause) {

        def this(message: String) =
            this(message, null);
    }

    /**
     * Information about a problem that occurred in {@link Config#checkValid}. A
     * {@link ConfigException.ValidationFailed} exception thrown from
     * <code>checkValid()</code> includes a list of problems encountered.
     */
    @SerialVersionUID(1L)
    class ValidationProblem(path: String, origin: ConfigOrigin, problem: String) extends Serializable {

        final private val _path: String = path
        @transient final private val _origin: ConfigOrigin = origin
        final private val _problem: String = problem

        /**
         * Returns the config setting causing the problem.
         * @return the path of the problem setting
         */
        def path(): String = _path

        /**
         * Returns where the problem occurred (origin may include info on the
         * file, line number, etc.).
         * @return the origin of the problem setting
         */
        def origin(): ConfigOrigin = _origin

        /**
         * Returns a description of the problem.
         * @return description of the problem
         */
        def problem(): String = _problem

        // We customize serialization because ConfigOrigin isn't
        // serializable and we don't want it to be
        @throws(classOf[IOException])
        private def writeObject(out: java.io.ObjectOutputStream): Unit = {
            out.defaultWriteObject();
            ConfigImplUtil.writeOrigin(out, _origin);
        }

        @throws(classOf[IOException])
        @throws(classOf[ClassNotFoundException])
        private def readObject(in: java.io.ObjectInputStream): Unit = {
            in.defaultReadObject();
            val origin: ConfigOrigin = ConfigImplUtil.readOrigin(in);
            setOriginField(this, classOf[ValidationProblem], origin);
        }

        override def toString(): String =
            "ValidationProblem(" + _path + "," + _origin + "," + _problem + ")";
    }

    /**
     * Exception indicating that {@link Config#checkValid} found validity
     * problems. The problems are available via the {@link #problems()} method.
     * The <code>getMessage()</code> of this exception is a potentially very
     * long string listing all the problems found.
     */
    @SerialVersionUID(1L)
    class ValidationFailed(problems: Iterable[ValidationProblem]) extends ConfigException(ValidationFailed.makeMessage(problems), null) {

        final private val _problems: Iterable[ValidationProblem] = problems

        def problems(): Iterable[ValidationProblem] = _problems

    }
    object ValidationFailed {
        private def makeMessage(problems: Iterable[ValidationProblem]) = {
            val sb: StringBuilder = new StringBuilder();
            for (p <- problems) {
                sb.append(p.origin().description());
                sb.append(": ");
                sb.append(p.path());
                sb.append(": ");
                sb.append(p.problem());
                sb.append(", ");
            }
            if (sb.length() == 0)
                throw new ConfigException.BugOrBroken("ValidationFailed must have a non-empty list of problems");
            sb.setLength(sb.length() - 2); // chop comma and space

            sb.toString();
        }
    }

    /**
     * Some problem with a JavaBean we are trying to initialize.
     * @since 1.3.0
     */
    @SerialVersionUID(1L)
    class BadBean(message: String, cause: Throwable) extends BugOrBroken(message, cause) {

        def this(message: String) =
            this(message, null);
    }

    /**
     * Exception that doesn't fall into any other category.
     */
    @SerialVersionUID(1L)
    class Generic(message: String, cause: Throwable) extends ConfigException(message, cause) {

        def this(message: String) =
            this(message, null);
    }

}
