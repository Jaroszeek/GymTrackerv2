package android.database

/**
 * Replaces the android.jar stub in JVM tests, which drops the message: Room's @Upsert tells a key
 * conflict (then it updates) from other errors by the message, so with the stub every upsert of an
 * existing row failed. Test classes come before android.jar on the test classpath.
 */
open class SQLException @JvmOverloads constructor(error: String? = null, cause: Throwable? = null) : RuntimeException(error, cause)
