package com.gtr.electricis

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.location.Location
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.max

private const val MAX_PHOTO = 2 * 1024 * 1024
private const val PHOTO_SIDE = 1600

fun canReadLocation(context:Context):Boolean = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION
).any { ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED }

@SuppressLint("MissingPermission")
suspend fun captureStartLocation(context:Context):WorkStart {
    require(canReadLocation(context)) { "Autoriza la ubicación para registrar el inicio." }
    val manager=context.getSystemService(LocationManager::class.java)
    require(manager!=null && LocationManagerCompat.isLocationEnabled(manager)) {
        "Activa la ubicación del dispositivo y vuelve a intentar."
    }
    val precise=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    val providers=buildList {
        if(precise && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
    }
    for(provider in providers) {
        val location=withTimeoutOrNull(if(provider==LocationManager.GPS_PROVIDER) 18_000L else 7_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal=CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager,provider,signal,ContextCompat.getMainExecutor(context)) {
                        if(continuation.isActive) continuation.resume(it)
                    }
                } catch (_: IllegalArgumentException) {
                    if(continuation.isActive) continuation.resume(null)
                } catch (_: SecurityException) {
                    if(continuation.isActive) continuation.resume(null)
                }
            }
        }
        if(location!=null && location.hasAccuracy() && location.accuracy in 0f..100_000f) {
            return WorkStart(location.latitude,location.longitude,location.accuracy)
        }
    }
    error("No se pudo obtener la ubicación. Revisa el permiso y la señal GPS. En el emulador, fija una ubicación en sus controles.")
}

private fun oldBitmap(context:Context, uri:Uri):Bitmap {
    val resolver=context.contentResolver
    val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
    require(options.outWidth>0 && options.outHeight>0) { "No se pudo leer la foto." }
    options.inSampleSize=1
    while(max(options.outWidth,options.outHeight)/options.inSampleSize>PHOTO_SIDE*2) options.inSampleSize*=2
    options.inJustDecodeBounds=false
    val bitmap=requireNotNull(resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }) {
        "No se pudo leer la foto."
    }
    val orientation=runCatching {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull()
    val matrix=Matrix()
    when(orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f,1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f,-1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f,1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f,1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also {
        if(it!==bitmap) bitmap.recycle()
    }
}

suspend fun prepareEvidence(context:Context,uri:Uri):ByteArray=withContext(Dispatchers.IO) {
    val decoded=try {
        if(Build.VERSION.SDK_INT>=28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                val side=max(info.size.width,info.size.height)
                if(side>PHOTO_SIDE) decoder.setTargetSize(
                    (info.size.width.toLong()*PHOTO_SIDE/side).toInt().coerceAtLeast(1),
                    (info.size.height.toLong()*PHOTO_SIDE/side).toInt().coerceAtLeast(1)
                )
            }
        } else oldBitmap(context,uri)
    } catch(_:java.io.IOException) {
        error("No se pudo leer la foto. Selecciona otra imagen.")
    }
    val side=max(decoded.width,decoded.height)
    val scaled=if(side>PHOTO_SIDE) Bitmap.createScaledBitmap(decoded,
        (decoded.width.toLong()*PHOTO_SIDE/side).toInt().coerceAtLeast(1),
        (decoded.height.toLong()*PHOTO_SIDE/side).toInt().coerceAtLeast(1),true) else decoded
    try {
        val output=ByteArrayOutputStream()
        check(scaled.compress(Bitmap.CompressFormat.JPEG,82,output)) { "No se pudo preparar la foto." }
        require(output.size()<=MAX_PHOTO) { "La foto no debe superar los 2 MB." }
        output.toByteArray()
    } finally {
        if(scaled!==decoded) scaled.recycle()
        decoded.recycle()
    }
}
