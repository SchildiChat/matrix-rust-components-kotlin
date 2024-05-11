// This file was autogenerated by some hot garbage in the `uniffi` crate.
// Trust me, you don't want to mess with it!

@file:Suppress("NAME_SHADOWING")

package uniffi.matrix_sdk;

// Common helper code.
//
// Ideally this would live in a separate .kt file where it can be unittested etc
// in isolation, and perhaps even published as a re-useable package.
//
// However, it's important that the details of how this helper code works (e.g. the
// way that different builtin types are passed across the FFI) exactly match what's
// expected by the Rust code on the other side of the interface. In practice right
// now that means coming from the exact some version of `uniffi` that was used to
// compile the Rust component. The easiest way to ensure this is to bundle the Kotlin
// helpers directly inline like we're doing here.

import com.sun.jna.Library
import com.sun.jna.IntegerType
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Callback
import com.sun.jna.ptr.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

// This is a helper for safely working with byte buffers returned from the Rust code.
// A rust-owned buffer is represented by its capacity, its current length, and a
// pointer to the underlying data.

@Structure.FieldOrder("capacity", "len", "data")
open class RustBuffer : Structure() {
    // Note: `capacity` and `len` are actually `ULong` values, but JVM only supports signed values.
    // When dealing with these fields, make sure to call `toULong()`.
    @JvmField var capacity: Long = 0
    @JvmField var len: Long = 0
    @JvmField var data: Pointer? = null

    class ByValue: RustBuffer(), Structure.ByValue
    class ByReference: RustBuffer(), Structure.ByReference

   internal fun setValue(other: RustBuffer) {
        capacity = other.capacity
        len = other.len
        data = other.data
    }

    companion object {
        internal fun alloc(size: ULong = 0UL) = uniffiRustCall() { status ->
            // Note: need to convert the size to a `Long` value to make this work with JVM.
            UniffiLib.INSTANCE.ffi_matrix_sdk_rustbuffer_alloc(size.toLong(), status)
        }.also {
            if(it.data == null) {
               throw RuntimeException("RustBuffer.alloc() returned null data pointer (size=${size})")
           }
        }

        internal fun create(capacity: ULong, len: ULong, data: Pointer?): RustBuffer.ByValue {
            var buf = RustBuffer.ByValue()
            buf.capacity = capacity.toLong()
            buf.len = len.toLong()
            buf.data = data
            return buf
        }

        internal fun free(buf: RustBuffer.ByValue) = uniffiRustCall() { status ->
            UniffiLib.INSTANCE.ffi_matrix_sdk_rustbuffer_free(buf, status)
        }
    }

    @Suppress("TooGenericExceptionThrown")
    fun asByteBuffer() =
        this.data?.getByteBuffer(0, this.len.toLong())?.also {
            it.order(ByteOrder.BIG_ENDIAN)
        }
}

/**
 * The equivalent of the `*mut RustBuffer` type.
 * Required for callbacks taking in an out pointer.
 *
 * Size is the sum of all values in the struct.
 */
class RustBufferByReference : ByReference(16) {
    /**
     * Set the pointed-to `RustBuffer` to the given value.
     */
    fun setValue(value: RustBuffer.ByValue) {
        // NOTE: The offsets are as they are in the C-like struct.
        val pointer = getPointer()
        pointer.setLong(0, value.capacity)
        pointer.setLong(8, value.len)
        pointer.setPointer(16, value.data)
    }

    /**
     * Get a `RustBuffer.ByValue` from this reference.
     */
    fun getValue(): RustBuffer.ByValue {
        val pointer = getPointer()
        val value = RustBuffer.ByValue()
        value.writeField("capacity", pointer.getLong(0))
        value.writeField("len", pointer.getLong(8))
        value.writeField("data", pointer.getLong(16))

        return value
    }
}

// This is a helper for safely passing byte references into the rust code.
// It's not actually used at the moment, because there aren't many things that you
// can take a direct pointer to in the JVM, and if we're going to copy something
// then we might as well copy it into a `RustBuffer`. But it's here for API
// completeness.

@Structure.FieldOrder("len", "data")
open class ForeignBytes : Structure() {
    @JvmField var len: Int = 0
    @JvmField var data: Pointer? = null

    class ByValue : ForeignBytes(), Structure.ByValue
}
// The FfiConverter interface handles converter types to and from the FFI
//
// All implementing objects should be public to support external types.  When a
// type is external we need to import it's FfiConverter.
public interface FfiConverter<KotlinType, FfiType> {
    // Convert an FFI type to a Kotlin type
    fun lift(value: FfiType): KotlinType

    // Convert an Kotlin type to an FFI type
    fun lower(value: KotlinType): FfiType

    // Read a Kotlin type from a `ByteBuffer`
    fun read(buf: ByteBuffer): KotlinType

    // Calculate bytes to allocate when creating a `RustBuffer`
    //
    // This must return at least as many bytes as the write() function will
    // write. It can return more bytes than needed, for example when writing
    // Strings we can't know the exact bytes needed until we the UTF-8
    // encoding, so we pessimistically allocate the largest size possible (3
    // bytes per codepoint).  Allocating extra bytes is not really a big deal
    // because the `RustBuffer` is short-lived.
    fun allocationSize(value: KotlinType): ULong

    // Write a Kotlin type to a `ByteBuffer`
    fun write(value: KotlinType, buf: ByteBuffer)

    // Lower a value into a `RustBuffer`
    //
    // This method lowers a value into a `RustBuffer` rather than the normal
    // FfiType.  It's used by the callback interface code.  Callback interface
    // returns are always serialized into a `RustBuffer` regardless of their
    // normal FFI type.
    fun lowerIntoRustBuffer(value: KotlinType): RustBuffer.ByValue {
        val rbuf = RustBuffer.alloc(allocationSize(value))
        try {
            val bbuf = rbuf.data!!.getByteBuffer(0, rbuf.capacity).also {
                it.order(ByteOrder.BIG_ENDIAN)
            }
            write(value, bbuf)
            rbuf.writeField("len", bbuf.position().toLong())
            return rbuf
        } catch (e: Throwable) {
            RustBuffer.free(rbuf)
            throw e
        }
    }

    // Lift a value from a `RustBuffer`.
    //
    // This here mostly because of the symmetry with `lowerIntoRustBuffer()`.
    // It's currently only used by the `FfiConverterRustBuffer` class below.
    fun liftFromRustBuffer(rbuf: RustBuffer.ByValue): KotlinType {
        val byteBuf = rbuf.asByteBuffer()!!
        try {
           val item = read(byteBuf)
           if (byteBuf.hasRemaining()) {
               throw RuntimeException("junk remaining in buffer after lifting, something is very wrong!!")
           }
           return item
        } finally {
            RustBuffer.free(rbuf)
        }
    }
}

// FfiConverter that uses `RustBuffer` as the FfiType
public interface FfiConverterRustBuffer<KotlinType>: FfiConverter<KotlinType, RustBuffer.ByValue> {
    override fun lift(value: RustBuffer.ByValue) = liftFromRustBuffer(value)
    override fun lower(value: KotlinType) = lowerIntoRustBuffer(value)
}
// A handful of classes and functions to support the generated data structures.
// This would be a good candidate for isolating in its own ffi-support lib.

internal const val UNIFFI_CALL_SUCCESS = 0.toByte()
internal const val UNIFFI_CALL_ERROR = 1.toByte()
internal const val UNIFFI_CALL_UNEXPECTED_ERROR = 2.toByte()

@Structure.FieldOrder("code", "error_buf")
internal open class UniffiRustCallStatus : Structure() {
    @JvmField var code: Byte = 0
    @JvmField var error_buf: RustBuffer.ByValue = RustBuffer.ByValue()

    class ByValue: UniffiRustCallStatus(), Structure.ByValue

    fun isSuccess(): Boolean {
        return code == UNIFFI_CALL_SUCCESS
    }

    fun isError(): Boolean {
        return code == UNIFFI_CALL_ERROR
    }

    fun isPanic(): Boolean {
        return code == UNIFFI_CALL_UNEXPECTED_ERROR
    }
}

class InternalException(message: String) : Exception(message)

// Each top-level error class has a companion object that can lift the error from the call status's rust buffer
interface UniffiRustCallStatusErrorHandler<E> {
    fun lift(error_buf: RustBuffer.ByValue): E;
}

// Helpers for calling Rust
// In practice we usually need to be synchronized to call this safely, so it doesn't
// synchronize itself

// Call a rust function that returns a Result<>.  Pass in the Error class companion that corresponds to the Err
private inline fun <U, E: Exception> uniffiRustCallWithError(errorHandler: UniffiRustCallStatusErrorHandler<E>, callback: (UniffiRustCallStatus) -> U): U {
    var status = UniffiRustCallStatus();
    val return_value = callback(status)
    uniffiCheckCallStatus(errorHandler, status)
    return return_value
}

// Check UniffiRustCallStatus and throw an error if the call wasn't successful
private fun<E: Exception> uniffiCheckCallStatus(errorHandler: UniffiRustCallStatusErrorHandler<E>, status: UniffiRustCallStatus) {
    if (status.isSuccess()) {
        return
    } else if (status.isError()) {
        throw errorHandler.lift(status.error_buf)
    } else if (status.isPanic()) {
        // when the rust code sees a panic, it tries to construct a rustbuffer
        // with the message.  but if that code panics, then it just sends back
        // an empty buffer.
        if (status.error_buf.len > 0) {
            throw InternalException(FfiConverterString.lift(status.error_buf))
        } else {
            throw InternalException("Rust panic")
        }
    } else {
        throw InternalException("Unknown rust call status: $status.code")
    }
}

// UniffiRustCallStatusErrorHandler implementation for times when we don't expect a CALL_ERROR
object UniffiNullRustCallStatusErrorHandler: UniffiRustCallStatusErrorHandler<InternalException> {
    override fun lift(error_buf: RustBuffer.ByValue): InternalException {
        RustBuffer.free(error_buf)
        return InternalException("Unexpected CALL_ERROR")
    }
}

// Call a rust function that returns a plain value
private inline fun <U> uniffiRustCall(callback: (UniffiRustCallStatus) -> U): U {
    return uniffiRustCallWithError(UniffiNullRustCallStatusErrorHandler, callback);
}

internal inline fun<T> uniffiTraitInterfaceCall(
    callStatus: UniffiRustCallStatus,
    makeCall: () -> T,
    writeReturn: (T) -> Unit,
) {
    try {
        writeReturn(makeCall())
    } catch(e: Exception) {
        callStatus.code = UNIFFI_CALL_UNEXPECTED_ERROR
        callStatus.error_buf = FfiConverterString.lower(e.toString())
    }
}

internal inline fun<T, reified E: Throwable> uniffiTraitInterfaceCallWithError(
    callStatus: UniffiRustCallStatus,
    makeCall: () -> T,
    writeReturn: (T) -> Unit,
    lowerError: (E) -> RustBuffer.ByValue
) {
    try {
        writeReturn(makeCall())
    } catch(e: Exception) {
        if (e is E) {
            callStatus.code = UNIFFI_CALL_ERROR
            callStatus.error_buf = lowerError(e)
        } else {
            callStatus.code = UNIFFI_CALL_UNEXPECTED_ERROR
            callStatus.error_buf = FfiConverterString.lower(e.toString())
        }
    }
}
// Map handles to objects
//
// This is used pass an opaque 64-bit handle representing a foreign object to the Rust code.
internal class UniffiHandleMap<T: Any> {
    private val map = ConcurrentHashMap<Long, T>()
    private val counter = java.util.concurrent.atomic.AtomicLong(0)

    val size: Int
        get() = map.size

    // Insert a new object into the handle map and get a handle for it
    fun insert(obj: T): Long {
        val handle = counter.getAndAdd(1)
        map.put(handle, obj)
        return handle
    }

    // Get an object from the handle map
    fun get(handle: Long): T {
        return map.get(handle) ?: throw InternalException("UniffiHandleMap.get: Invalid handle")
    }

    // Remove an entry from the handlemap and get the Kotlin object back
    fun remove(handle: Long): T {
        return map.remove(handle) ?: throw InternalException("UniffiHandleMap: Invalid handle")
    }
}

// Contains loading, initialization code,
// and the FFI Function declarations in a com.sun.jna.Library.
@Synchronized
private fun findLibraryName(componentName: String): String {
    val libOverride = System.getProperty("uniffi.component.$componentName.libraryOverride")
    if (libOverride != null) {
        return libOverride
    }
    return "matrix_sdk_ffi"
}

private inline fun <reified Lib : Library> loadIndirect(
    componentName: String
): Lib {
    return Native.load<Lib>(findLibraryName(componentName), Lib::class.java)
}

// Define FFI callback types
internal interface UniffiRustFutureContinuationCallback : com.sun.jna.Callback {
    fun callback(`data`: Long,`pollResult`: Byte,)
}
internal interface UniffiForeignFutureFree : com.sun.jna.Callback {
    fun callback(`handle`: Long,)
}
internal interface UniffiCallbackInterfaceFree : com.sun.jna.Callback {
    fun callback(`handle`: Long,)
}
@Structure.FieldOrder("handle", "free")
internal open class UniffiForeignFuture(
    @JvmField internal var `handle`: Long = 0.toLong(),
    @JvmField internal var `free`: UniffiForeignFutureFree? = null,
) : Structure() {
    class UniffiByValue(
        `handle`: Long = 0.toLong(),
        `free`: UniffiForeignFutureFree? = null,
    ): UniffiForeignFuture(`handle`,`free`,), Structure.ByValue

   internal fun uniffiSetValue(other: UniffiForeignFuture) {
        `handle` = other.`handle`
        `free` = other.`free`
    }

}


























































// A JNA Library to expose the extern-C FFI definitions.
// This is an implementation detail which will be called internally by the public API.

internal interface UniffiLib : Library {
    companion object {
        internal val INSTANCE: UniffiLib by lazy {
            loadIndirect<UniffiLib>(componentName = "matrix_sdk")
            .also { lib: UniffiLib ->
                uniffiCheckContractApiVersion(lib)
                uniffiCheckApiChecksums(lib)
                }
        }
        
    }

    fun ffi_matrix_sdk_rustbuffer_alloc(`size`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): RustBuffer.ByValue
    fun ffi_matrix_sdk_rustbuffer_from_bytes(`bytes`: ForeignBytes.ByValue,uniffi_out_err: UniffiRustCallStatus, 
    ): RustBuffer.ByValue
    fun ffi_matrix_sdk_rustbuffer_free(`buf`: RustBuffer.ByValue,uniffi_out_err: UniffiRustCallStatus, 
    ): Unit
    fun ffi_matrix_sdk_rustbuffer_reserve(`buf`: RustBuffer.ByValue,`additional`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): RustBuffer.ByValue
    fun ffi_matrix_sdk_rust_future_poll_u8(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_u8(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_u8(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_u8(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Byte
    fun ffi_matrix_sdk_rust_future_poll_i8(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_i8(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_i8(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_i8(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Byte
    fun ffi_matrix_sdk_rust_future_poll_u16(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_u16(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_u16(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_u16(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Short
    fun ffi_matrix_sdk_rust_future_poll_i16(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_i16(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_i16(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_i16(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Short
    fun ffi_matrix_sdk_rust_future_poll_u32(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_u32(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_u32(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_u32(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Int
    fun ffi_matrix_sdk_rust_future_poll_i32(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_i32(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_i32(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_i32(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Int
    fun ffi_matrix_sdk_rust_future_poll_u64(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_u64(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_u64(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_u64(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Long
    fun ffi_matrix_sdk_rust_future_poll_i64(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_i64(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_i64(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_i64(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Long
    fun ffi_matrix_sdk_rust_future_poll_f32(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_f32(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_f32(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_f32(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Float
    fun ffi_matrix_sdk_rust_future_poll_f64(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_f64(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_f64(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_f64(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Double
    fun ffi_matrix_sdk_rust_future_poll_pointer(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_pointer(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_pointer(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_pointer(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Pointer
    fun ffi_matrix_sdk_rust_future_poll_rust_buffer(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_rust_buffer(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_rust_buffer(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_rust_buffer(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): RustBuffer.ByValue
    fun ffi_matrix_sdk_rust_future_poll_void(`handle`: Long,`callback`: UniffiRustFutureContinuationCallback,`callbackData`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_cancel_void(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_free_void(`handle`: Long,
    ): Unit
    fun ffi_matrix_sdk_rust_future_complete_void(`handle`: Long,uniffi_out_err: UniffiRustCallStatus, 
    ): Unit
    fun ffi_matrix_sdk_uniffi_contract_version(
    ): Int
    
}

private fun uniffiCheckContractApiVersion(lib: UniffiLib) {
    // Get the bindings contract version from our ComponentInterface
    val bindings_contract_version = 26
    // Get the scaffolding contract version by calling the into the dylib
    val scaffolding_contract_version = lib.ffi_matrix_sdk_uniffi_contract_version()
    if (bindings_contract_version != scaffolding_contract_version) {
        throw RuntimeException("UniFFI contract version mismatch: try cleaning and rebuilding your project")
    }
}

@Suppress("UNUSED_PARAMETER")
private fun uniffiCheckApiChecksums(lib: UniffiLib) {
}

// Async support

// Public interface members begin here.


// Interface implemented by anything that can contain an object reference.
//
// Such types expose a `destroy()` method that must be called to cleanly
// dispose of the contained objects. Failure to call this method may result
// in memory leaks.
//
// The easiest way to ensure this method is called is to use the `.use`
// helper method to execute a block and destroy the object at the end.
interface Disposable {
    fun destroy()
    companion object {
        fun destroy(vararg args: Any?) {
            args.filterIsInstance<Disposable>()
                .forEach(Disposable::destroy)
        }
    }
}

inline fun <T : Disposable?, R> T.use(block: (T) -> R) =
    try {
        block(this)
    } finally {
        try {
            // N.B. our implementation is on the nullable type `Disposable?`.
            this?.destroy()
        } catch (e: Throwable) {
            // swallow
        }
    }

/** Used to instantiate an interface without an actual pointer, for fakes in tests, mostly. */
object NoPointer

public object FfiConverterLong: FfiConverter<Long, Long> {
    override fun lift(value: Long): Long {
        return value
    }

    override fun read(buf: ByteBuffer): Long {
        return buf.getLong()
    }

    override fun lower(value: Long): Long {
        return value
    }

    override fun allocationSize(value: Long) = 8UL

    override fun write(value: Long, buf: ByteBuffer) {
        buf.putLong(value)
    }
}

public object FfiConverterString: FfiConverter<String, RustBuffer.ByValue> {
    // Note: we don't inherit from FfiConverterRustBuffer, because we use a
    // special encoding when lowering/lifting.  We can use `RustBuffer.len` to
    // store our length and avoid writing it out to the buffer.
    override fun lift(value: RustBuffer.ByValue): String {
        try {
            val byteArr = ByteArray(value.len.toInt())
            value.asByteBuffer()!!.get(byteArr)
            return byteArr.toString(Charsets.UTF_8)
        } finally {
            RustBuffer.free(value)
        }
    }

    override fun read(buf: ByteBuffer): String {
        val len = buf.getInt()
        val byteArr = ByteArray(len)
        buf.get(byteArr)
        return byteArr.toString(Charsets.UTF_8)
    }

    fun toUtf8(value: String): ByteBuffer {
        // Make sure we don't have invalid UTF-16, check for lone surrogates.
        return Charsets.UTF_8.newEncoder().run {
            onMalformedInput(CodingErrorAction.REPORT)
            encode(CharBuffer.wrap(value))
        }
    }

    override fun lower(value: String): RustBuffer.ByValue {
        val byteBuf = toUtf8(value)
        // Ideally we'd pass these bytes to `ffi_bytebuffer_from_bytes`, but doing so would require us
        // to copy them into a JNA `Memory`. So we might as well directly copy them into a `RustBuffer`.
        val rbuf = RustBuffer.alloc(byteBuf.limit().toULong())
        rbuf.asByteBuffer()!!.put(byteBuf)
        return rbuf
    }

    // We aren't sure exactly how many bytes our string will be once it's UTF-8
    // encoded.  Allocate 3 bytes per UTF-16 code unit which will always be
    // enough.
    override fun allocationSize(value: String): ULong {
        val sizeForLength = 4UL
        val sizeForString = value.length.toULong() * 3UL
        return sizeForLength + sizeForString
    }

    override fun write(value: String, buf: ByteBuffer) {
        val byteBuf = toUtf8(value)
        buf.putInt(byteBuf.limit())
        buf.put(byteBuf)
    }
}



/**
 * A set of common power levels required for various operations within a room,
 * that can be applied as a single operation. When updating these
 * settings, any levels that are `None` will remain unchanged.
 */
data class RoomPowerLevelChanges (
    /**
     * The level required to ban a user.
     */
    var `ban`: kotlin.Long? = null, 
    /**
     * The level required to invite a user.
     */
    var `invite`: kotlin.Long? = null, 
    /**
     * The level required to kick a user.
     */
    var `kick`: kotlin.Long? = null, 
    /**
     * The level required to redact an event.
     */
    var `redact`: kotlin.Long? = null, 
    /**
     * The default level required to send message events.
     */
    var `eventsDefault`: kotlin.Long? = null, 
    /**
     * The default level required to send state events.
     */
    var `stateDefault`: kotlin.Long? = null, 
    /**
     * The default power level for every user in the room.
     */
    var `usersDefault`: kotlin.Long? = null, 
    /**
     * The level required to change the room's name.
     */
    var `roomName`: kotlin.Long? = null, 
    /**
     * The level required to change the room's avatar.
     */
    var `roomAvatar`: kotlin.Long? = null, 
    /**
     * The level required to change the room's topic.
     */
    var `roomTopic`: kotlin.Long? = null
) {
    
    companion object
}

public object FfiConverterTypeRoomPowerLevelChanges: FfiConverterRustBuffer<RoomPowerLevelChanges> {
    override fun read(buf: ByteBuffer): RoomPowerLevelChanges {
        return RoomPowerLevelChanges(
            FfiConverterOptionalLong.read(buf),
            FfiConverterOptionalLong.read(buf),
            FfiConverterOptionalLong.read(buf),
            FfiConverterOptionalLong.read(buf),
            FfiConverterOptionalLong.read(buf),
            FfiConverterOptionalLong.read(buf),
            FfiConverterOptionalLong.read(buf),
            FfiConverterOptionalLong.read(buf),
            FfiConverterOptionalLong.read(buf),
            FfiConverterOptionalLong.read(buf),
        )
    }

    override fun allocationSize(value: RoomPowerLevelChanges) = (
            FfiConverterOptionalLong.allocationSize(value.`ban`) +
            FfiConverterOptionalLong.allocationSize(value.`invite`) +
            FfiConverterOptionalLong.allocationSize(value.`kick`) +
            FfiConverterOptionalLong.allocationSize(value.`redact`) +
            FfiConverterOptionalLong.allocationSize(value.`eventsDefault`) +
            FfiConverterOptionalLong.allocationSize(value.`stateDefault`) +
            FfiConverterOptionalLong.allocationSize(value.`usersDefault`) +
            FfiConverterOptionalLong.allocationSize(value.`roomName`) +
            FfiConverterOptionalLong.allocationSize(value.`roomAvatar`) +
            FfiConverterOptionalLong.allocationSize(value.`roomTopic`)
    )

    override fun write(value: RoomPowerLevelChanges, buf: ByteBuffer) {
            FfiConverterOptionalLong.write(value.`ban`, buf)
            FfiConverterOptionalLong.write(value.`invite`, buf)
            FfiConverterOptionalLong.write(value.`kick`, buf)
            FfiConverterOptionalLong.write(value.`redact`, buf)
            FfiConverterOptionalLong.write(value.`eventsDefault`, buf)
            FfiConverterOptionalLong.write(value.`stateDefault`, buf)
            FfiConverterOptionalLong.write(value.`usersDefault`, buf)
            FfiConverterOptionalLong.write(value.`roomName`, buf)
            FfiConverterOptionalLong.write(value.`roomAvatar`, buf)
            FfiConverterOptionalLong.write(value.`roomTopic`, buf)
    }
}



/**
 * Settings for end-to-end encryption features.
 */

enum class BackupDownloadStrategy {
    
    /**
     * Automatically download all room keys from the backup when the backup
     * recovery key has been received. The backup recovery key can be received
     * in two ways:
     *
     * 1. Received as a `m.secret.send` to-device event, after a successful
     * interactive verification.
     * 2. Imported from secret storage (4S) using the
     * [`SecretStore::import_secrets()`] method.
     *
     * [`SecretStore::import_secrets()`]: crate::encryption::secret_storage::SecretStore::import_secrets
     */
    ONE_SHOT,
    /**
     * Attempt to download a single room key if an event fails to be decrypted.
     */
    AFTER_DECRYPTION_FAILURE,
    /**
     * Don't download any room keys automatically. The user can manually
     * download room keys using the [`Backups::download_room_key()`] methods.
     *
     * This is the default option.
     */
    MANUAL;
    companion object
}


public object FfiConverterTypeBackupDownloadStrategy: FfiConverterRustBuffer<BackupDownloadStrategy> {
    override fun read(buf: ByteBuffer) = try {
        BackupDownloadStrategy.values()[buf.getInt() - 1]
    } catch (e: IndexOutOfBoundsException) {
        throw RuntimeException("invalid enum value, something is very wrong!!", e)
    }

    override fun allocationSize(value: BackupDownloadStrategy) = 4UL

    override fun write(value: BackupDownloadStrategy, buf: ByteBuffer) {
        buf.putInt(value.ordinal + 1)
    }
}





/**
 * The role of a member in a room.
 */

enum class RoomMemberRole {
    
    /**
     * The member is an administrator.
     */
    ADMINISTRATOR,
    /**
     * The member is a moderator.
     */
    MODERATOR,
    /**
     * The member is a regular user.
     */
    USER;
    companion object
}


public object FfiConverterTypeRoomMemberRole: FfiConverterRustBuffer<RoomMemberRole> {
    override fun read(buf: ByteBuffer) = try {
        RoomMemberRole.values()[buf.getInt() - 1]
    } catch (e: IndexOutOfBoundsException) {
        throw RuntimeException("invalid enum value, something is very wrong!!", e)
    }

    override fun allocationSize(value: RoomMemberRole) = 4UL

    override fun write(value: RoomMemberRole, buf: ByteBuffer) {
        buf.putInt(value.ordinal + 1)
    }
}






public object FfiConverterOptionalLong: FfiConverterRustBuffer<kotlin.Long?> {
    override fun read(buf: ByteBuffer): kotlin.Long? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterLong.read(buf)
    }

    override fun allocationSize(value: kotlin.Long?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterLong.allocationSize(value)
        }
    }

    override fun write(value: kotlin.Long?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterLong.write(value, buf)
        }
    }
}

