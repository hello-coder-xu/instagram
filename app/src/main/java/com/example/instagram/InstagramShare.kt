package com.example.instagram

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * instagram分享
 */
class InstagramShare {

    companion object {
        private var instance: InstagramShare? = null
            get() {
                if (field == null) {
                    field = InstagramShare()
                }
                return field
            }

        fun get(): InstagramShare {
            return instance!!
        }
    }

    private val mineType = "image/png"

    /**
     * 获取图片名
     */
    private fun getImageName(value: String): String {
        val result = "instagram_share_$value"
        Log.d("instagram", "当前文件名：$result")
        return result
    }

    /**
     * 分享媒体图片
     * @relativePath 媒体文件夹
     * @fileName  媒体中文件名 《需要有后缀》
     */
    fun shareMediaFile(context: Context, imageName: String, readRequest: Int = 101) {
        if (!imageName.endsWith(".png")) {
            Log.e("instagram", "分享媒体图片 错误:图片后缀不是.png")
            return
        }
        // 判断读取权限
        val readPermission = checkPermissionRead(context, readRequest)
        if (!readPermission) {
            Log.e("instagram", "分享媒体图片 读取权限判断 $readPermission")
            return
        }
        val uri = getMediaStoreUri(context, imageName)
        if (uri != null) {
            Log.d("instagram", "分享媒体图片 已找到图片")
            shareToInstagram(context, uri)
        } else {
            Log.d("instagram", "分享媒体图片 错误:找不到对应图片")
        }
    }

    /**
     * 分享本地《非媒体》图片
     * 注意：本地图片需要插入到媒体中才能分享
     * @localFilePath 本地图片完整路径
     */
    fun shareLocalFile(
        context: Context,
        localFilePath: String,
        readRequest: Int = 101
    ) {
        if (localFilePath.isEmpty()) {
            Log.e("instagram", "分享本地图片 地址不能为空")
            return
        }
        val imageName = getImageName("${localFilePath.hashCode()}.png")
        // 判断读取权限
        val readPermission = checkPermissionRead(context, readRequest)
        if (!readPermission) {
            Log.e("instagram", "分享本地图片 读取权限判断 $readPermission")
            return
        }
        // 检查媒体中是否存在图片
        val uri = getMediaStoreUri(context, imageName)
        if (uri == null) {
            // 不存在:把文件转换成bitmap
            val file = File(localFilePath)
            if (!file.exists()) {
                Log.e("instagram", "分享本地图片 错误:文件不存在")
                return
            }
            Log.d("instagram", "分享本地图片 图片转bitmap")
            val bitmap = BitmapFactory.decodeFile(localFilePath)
            // 插入到media中去
            val tempUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                insertBitmapToMediaByAbove10(context, bitmap, imageName)
            } else {
                insertBitmapToMediaByBelow10(context, bitmap, imageName)
            }
            // 分享到instagram
            if (tempUri != null) shareToInstagram(context, tempUri)
        } else {
            Log.d("instagram", "分享本地图片 已找到图片")
            shareToInstagram(context, uri)
        }
    }

    /**
     * 分享网络图片
     * 注意：
     * 1，网络图片需要下载到本地
     * 2，本地图片插入到媒体中才能分享
     * @networkImageUrl 网络图片链接
     * @imageLoadStatus 状态回调
     */
    fun shareNetwork(
        context: Context,
        networkImageUrl: String,
        imageLoadStatus: ImageLoadStatus?,
        writeRequestCode: Int = 100,
        readRequest: Int = 101
    ) {
        if (networkImageUrl.isEmpty()) {
            Log.e("instagram", "分享网络图片 链接不能为空")
            return
        }
        val imageName = getImageName("${networkImageUrl.hashCode()}.png")
        // 判断读取权限
        val readPermission = checkPermissionRead(context, readRequest)
        if (!readPermission) {
            Log.e("instagram", "分享网络图片 读取权限判断 $readPermission")
            return
        }
        // 判断媒体是否存在图片
        val uri = getMediaStoreUri(context, imageName)
        if (uri != null) {
            Log.d("instagram", "分享网络图片 已找到图片")
            // 存在：直接分享
            shareToInstagram(context, uri)
            imageLoadStatus?.openShareResult(true)
        } else {
            // 不存在：
            // 1，下载再插入到媒体中去
            // 2，分享到instagram
            Log.d("instagram", "分享网络图片 检查写入权限")
            val hasPermission = checkPermissionWrite(context, writeRequestCode)
            if (hasPermission) {
                Log.d("instagram", "分享网络图片 开始下载")
                imageLoadStatus?.loading()
                loadImage(networkImageUrl) { bitmap ->
                    if (bitmap != null) {
                        Log.d("instagram", "分享网络图片 下载成功")
                        imageLoadStatus?.loadResult(true)
                        val tempUri: Uri? = insertBitmapToMedia(context, bitmap, imageName)
                        // 分享到instagram
                        if (tempUri != null) {
                            shareToInstagram(context, tempUri)
                            imageLoadStatus?.openShareResult(true)
                        } else {
                            imageLoadStatus?.openShareResult(false)
                        }
                    } else {
                        Log.d("instagram", "分享网络图片 下载失败")
                        imageLoadStatus?.loadResult(false)
                    }
                }
            }
        }
    }

    /**
     * 分享资源图片
     */
    fun shareResource(
        context: Context,
        drawable: Int,
        writeRequestCode: Int = 100,
        readRequest: Int = 101
    ) {
        val imageName = getImageName("$drawable.png")
        // 判断读取权限
        val readPermission = checkPermissionRead(context, readRequest)
        if (!readPermission) {
            Log.e("instagram", "分享资源图片 读取权限判断 $readPermission")
            return
        }
        var uri = getMediaStoreUri(context, imageName)
        if (uri == null) {
            Log.d("instagram", "分享资源图片 本地未找到")
            val bitmap = BitmapFactory.decodeResource(
                context.resources, drawable
            )
            Log.d("instagram", "分享资源图片 资源转bitmap")
            Log.d("instagram", "分享网络图片 检查写入权限")
            val hasPermission = checkPermissionWrite(context, writeRequestCode)
            if (hasPermission) {
                uri = insertBitmapToMedia(context, bitmap, imageName)
            }
        }
        if (uri != null) {
            Log.d("instagram", "分享资源图片 本地已找到")
            shareToInstagram(context, uri)
        }
    }


    /**
     * 获取媒体中的uri
     */
    private fun getMediaStoreUri(
        context: Context, fileName: String
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getMediaStoreUriAbove10(context, fileName)
        } else {
            getMediaStoreUriBelow10(context, fileName)
        }
    }


    /**
     * 获取媒体中的uri
     * android 10 以下
     */
    private fun getMediaStoreUriBelow10(
        context: Context, fileName: String
    ): Uri? {
        Log.d("instagram", "android 10 以上 检查图片是否存在")
        val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.TITLE,
        )
        var uri: Uri? = null
        val cursor: Cursor? = context.contentResolver.query(
            external,
            projection,
            MediaStore.Files.FileColumns.TITLE + " LIKE ? ",
            arrayOf(fileName),
            null
        )
        Log.d("instagram", "android 10 以上 检查结果 =${cursor?.count}")
        while (cursor?.moveToNext() == true) {
            val tempData = cursor.getString(3)
            if (tempData.contains(fileName)) {
                uri = ContentUris.withAppendedId(external, cursor.getLong(0))
                break
            }
        }
        cursor?.close()
        return uri
    }

    /**
     * 获取媒体中的uri
     * android 10 以上(包含10)
     */
    private fun getMediaStoreUriAbove10(
        context: Context, fileName: String
    ): Uri? {
        Log.d("instagram", "android 10 以下 检查图片是否存在")
        val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.TITLE,
        )
        val folder = context.resources.getString(R.string.app_name)
        var uri: Uri? = null
        val relativeName =
            Environment.getExternalStorageDirectory().absolutePath + File.separator + Environment.DIRECTORY_PICTURES + File.separator + folder + File.separator + fileName
        val cursor: Cursor? = context.contentResolver.query(
            external,
            projection,
            MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ? ",
            arrayOf(fileName),
            null
        )
        Log.d("instagram", "android 10 以下 检查结果 =${cursor?.count}")
        while (cursor?.moveToNext() == true) {
            val tempData = cursor.getString(2)
            if (tempData.contains(relativeName)) {
                uri = ContentUris.withAppendedId(external, cursor.getLong(0))
                break
            }
        }
        cursor?.close()
        return uri
    }

    /**
     * 分享到instagram
     */
    private fun shareToInstagram(context: Context, uri: Uri) {
        Log.d("instagram", "分享到instagram 开始分享")
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "image/*"
        val clipData = ClipData.newRawUri("Image", uri)
        intent.clipData = clipData
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        val target = Intent.createChooser(intent, "Share Image")
        target?.let { context.startActivity(it) }
    }

    /**
     * 检查存储权限
     */
    private fun checkPermissionWrite(context: Context, requestCode: Int = 100): Boolean {
        // 判断是否android10 以上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true
        } else {
            val hasWritePermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasWritePermission) {
                //无权限-权限申请
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    requestCode
                )
                Log.e("instagram", "分享网络图片 无写入权限")
                return false
            }
            Log.d("instagram", "分享网络图片 有写入权限")
            return true
        }
    }

    /**
     * 检查读取权限
     */
    private fun checkPermissionRead(context: Context, requestCode: Int = 101): Boolean {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadPermission) {
            //无权限-权限申请
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                requestCode
            )
            Log.d("instagram", "分享网络图片 无读取权限")
            return false
        }
        Log.d("instagram", "分享网络图片 有读取权限")
        return true
    }


    /**
     * 下载图片
     */
    private fun loadImage(
        downloadUrl: String,
        imageNetworkLoadResult: ImageNetworkLoadResult
    ) {
        object : Thread() {
            override fun run() {
                // 下载图片
                val bitmap: Bitmap? = downloadImg(downloadUrl, 5000, 5000)
                // 回调
                imageNetworkLoadResult.callBack(bitmap)
            }
        }.start()
    }


    private fun insertBitmapToMedia(context: Context, picBitmap: Bitmap, imageName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insertBitmapToMediaByAbove10(context, picBitmap, imageName)
        } else {
            insertBitmapToMediaByBelow10(context, picBitmap, imageName)
        }
    }

    /**
     * 保存Bitmap到相册
     * @imageName 图片名
     * @mimeType 媒体类型
     * android 10 以上(包含10)
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertBitmapToMediaByAbove10(
        context: Context, picBitmap: Bitmap, imageName: String
    ): Uri? {
        Log.d("instagram", "android 10 以上 bitmap插入到media 开始")
        val contentValues = ContentValues().apply {
            this.put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
            this.put(MediaStore.MediaColumns.MIME_TYPE, mineType)
            val imageTime = System.currentTimeMillis()
            this.put(MediaStore.MediaColumns.DATE_ADDED, imageTime / 1000)
            this.put(MediaStore.MediaColumns.DATE_MODIFIED, imageTime / 1000)
            // 判断是否android10 以上
            // 设置相对路径（自动创建文件夹）
            val folder = context.resources.getString(R.string.app_name)
            val relativeName =
                Environment.DIRECTORY_PICTURES + File.separator + folder + File.separator
            this.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeName)
            // 设置独占锁：耗时操作，独占访问权限，完成操作需复位
            this.put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver: ContentResolver = context.contentResolver

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                // First, write the actual data for our screenshot
                resolver.openOutputStream(uri).use { out ->
                    if (!picBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("android 10 以上 Failed to compress")
                    }
                }
                // android10 以上
                // 复位（解除）独占权限
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                val updateResult = resolver.update(uri, contentValues, null, null)
                Log.d("instagram", "android 10 以上 bitmap插入到media 更新结果：$updateResult")
            } catch (e: IOException) {
                Log.e("instagram", "android 10 以上 bitmap插入到media 插入失败 $e")
                return null
            }
        } else {
            Log.e("instagram", "android 10 以上 bitmap插入到media 插入失败")
        }
        return uri
    }

    /**
     * 保存Bitmap到相册
     * @imageName 图片名
     * @mimeType 媒体类型
     * android 10 以下
     */
    private fun insertBitmapToMediaByBelow10(
        context: Context, picBitmap: Bitmap, imageName: String
    ): Uri? {
        Log.d("instagram", "android 10 以下 bitmap插入到media 开始")
        val contentValues = ContentValues().apply {
            this.put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
            this.put(MediaStore.MediaColumns.MIME_TYPE, mineType)
            this.put(MediaStore.MediaColumns.TITLE, imageName)
            val imageTime = System.currentTimeMillis()
            this.put(MediaStore.MediaColumns.DATE_ADDED, imageTime / 1000)
            this.put(MediaStore.MediaColumns.DATE_MODIFIED, imageTime / 1000)
        }
        val resolver: ContentResolver = context.contentResolver

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri).use { out ->
                    if (!picBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("android 10 以下 Failed to compress")
                    }
                }
                Log.d("instagram", "android 10 以下 bitmap插入到media 插入成功")
            } catch (e: IOException) {
                Log.e("instagram", "android 10 以下 bitmap插入到media 插入失败 $e")
                return null
            }
        } else {
            Log.e("instagram", "android 10 以下 bitmap插入到media 插入失败")
        }
        return uri
    }

    /**
     * 根据URL下载图片
     */
    fun downloadImg(
        imageUrl: String?, connectTimeout: Int, readTimeout: Int
    ): Bitmap? {
        if (TextUtils.isEmpty(imageUrl)) {
            return null
        }
        val url: URL?
        var conn: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var bitmap: Bitmap? = null
        try {
            url = URL(imageUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            conn.doInput = true
            conn.connect()
            inputStream = conn.inputStream
            bitmap = BitmapFactory.decodeStream(inputStream)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
            inputStream?.close()
        }
        return bitmap
    }
}