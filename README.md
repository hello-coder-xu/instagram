# instagram分享
封装多种方式分享图片到instagram

# 分享图片方式
- 网络图片
```
  /// 网络图片
  val networkImageUrl ="https://img2.591.com.tw/house/2022/07/06/165709842179541103.jpg!900x.water3.jpg"
  InstagramShare.get().shareNetwork(context,
                networkImageUrl,
                "test.png",
                object : ImageLoadStatus {
                    override fun loading() {

                    }

                    override fun loadSuccess() {

                    }

                    override fun loadFail() {

                    }
                })
```
- 本地图片
```
// 本地图片
val fileName = "instagram.png"
val folder = context.resources.getString(R.string.app_name)
val externalCacheDirPath = externalCacheDir?.absoluteFile?.path
val localFilePath =externalCacheDirPath + File.separator + folder + File.separator + fileName
val mediaFileName = "localFile.png"
InstagramShare.get().shareLocalFile(context, localFilePath, mediaFileName)
```
- 资源图片
```
 // 资源图片
 InstagramShare.get().shareResource(context, R.drawable.instagram1, "instagram1.png")
```
- 媒体中的图片
```
// 媒体图片
InstagramShare.get().shareMediaFile(context, "instagram1.png")
```


- 核心类
```
package com.example.instagram

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
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
     * 分享媒体图片
     * @relativePath 媒体文件夹
     * @fileName  媒体中文件名 《需要有后缀》
     */
    fun shareMediaFile(context: Context, imageName: String) {
        if (!imageName.endsWith(".png")) {
            Log.e("instagram", "分享媒体图片 错误:图片后缀不是.png")
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
     * @mediaFileName 媒体对应图片名字 《需要有后缀》
     */
    fun shareLocalFile(context: Context, localFilePath: String, mediaFileName: String) {
        if (!mediaFileName.endsWith(".png")) {
            Log.e("instagram", "分享本地图片 错误:图片后缀不是.png")
            return
        }
        // 检查媒体中是否存在图片
        val uri = getMediaStoreUri(context, mediaFileName)
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
            val tempUri: Uri? = insertBitmapToMedia(context, bitmap, mediaFileName)
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
     * @imageName 图片名  《需要有后缀》
     * @mediaFolderName 媒体对应目录
     *
     */
    fun shareNetwork(
        context: Context,
        networkImageUrl: String,
        imageName: String,
        imageLoadStatus: ImageLoadStatus,
    ) {
        if (!imageName.endsWith(".png")) {
            Log.e("instagram", "分享网络图片 错误:图片后缀不是.png")
            return
        }
        // 判断媒体是否存在图片
        val uri = getMediaStoreUri(context, imageName)
        if (uri != null) {
            Log.d("instagram", "分享网络图片 已找到图片")
            // 存在：直接分享
            shareToInstagram(context, uri)
        } else {
            // 不存在：
            // 1，下载再插入到媒体中去
            // 2，分享到instagram
            Log.d("instagram", "分享网络图片 开始下载")
            imageLoadStatus.loading()
            checkPermissionAndDownloadImage(context, networkImageUrl) { bitmap ->
                if (bitmap != null) {
                    Log.d("instagram", "分享网络图片 下载成功")
                    imageLoadStatus.loadSuccess()
                    val tempUri: Uri? = insertBitmapToMedia(context, bitmap, imageName)
                    // 分享到instagram
                    if (tempUri != null) shareToInstagram(context, tempUri)
                } else {
                    Log.d("instagram", "分享网络图片 下载失败")
                    imageLoadStatus.loadFail()
                }
            }
        }
    }

    /**
     * 分享资源图片
     */
    fun shareResource(context: Context, drawable: Int, imageName: String) {
        if (!imageName.endsWith(".png")) {
            Log.e("instagram", "分享资源图片 错误:图片后缀不是.png")
            return
        }
        var uri = getMediaStoreUri(context, imageName)
        if (uri == null) {
            Log.d("instagram", "分享资源图片 本地未找到")
            val bitmap = BitmapFactory.decodeResource(
                context.resources,
                drawable
            )
            Log.d("instagram", "分享资源图片 资源转bitmap")
            uri = insertBitmapToMedia(context, bitmap, imageName)
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
        context: Context,
        fileName: String
    ): Uri? {
        val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection =
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
            )
        val folder = context.resources.getString(R.string.app_name)
        val relativeName =
            Environment.getExternalStorageDirectory().absolutePath +
                    File.separator + Environment.DIRECTORY_PICTURES +
                    File.separator + folder +
                    File.separator + fileName
        val cursor = context.contentResolver.query(
            external,
            projection,
            MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ? ",
            arrayOf(fileName),
            null
        )
        var uri: Uri? = null
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
     * 权限检查&&图片下载
     * @downloadUrl 下载地址
     * @fileName 图片名称
     * @callback 下载结果回调
     */
    private fun checkPermissionAndDownloadImage(
        context: Context,
        downloadUrl: String,
        imageNetworkLoadResult: ImageNetworkLoadResult
    ) {
        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasWritePermission) {
            //无权限-权限申请
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                100
            )
        }
        // 有权限
        object : Thread() {
            override fun run() {
                // 下载图片
                val bitmap: Bitmap? = downloadImg(downloadUrl, 5000, 5000)
                // 回调
                imageNetworkLoadResult.callBack(bitmap)
            }
        }.start()
    }


    /**
     * 保存Bitmap到相册
     * @imageName 图片名
     * @mimeType 媒体类型
     */
    private fun insertBitmapToMedia(
        context: Context,
        picBitmap: Bitmap,
        imageName: String
    ): Uri? {
        Log.d("instagram", "bitmap插入到media 开始")
        val contentValues = ContentValues().apply {
            this.put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
            this.put(MediaStore.MediaColumns.MIME_TYPE, mineType)
            val imageTime = System.currentTimeMillis()
            this.put(MediaStore.MediaColumns.DATE_ADDED, imageTime / 1000)
            this.put(MediaStore.MediaColumns.DATE_MODIFIED, imageTime / 1000)
            // 判断是否android10 以上
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 设置相对路径（自动创建文件夹）
                val folder = context.resources.getString(R.string.app_name)
                val relativeName =
                    Environment.DIRECTORY_PICTURES + File.separator + folder + File.separator
                this.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeName)
                // 设置独占锁：耗时操作，独占访问权限，完成操作需复位
                this.put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver: ContentResolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                // First, write the actual data for our screenshot
                resolver.openOutputStream(uri).use { out ->
                    if (!picBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("Failed to compress")
                    }
                }
                //判断是否android10 以上
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 复位（解除）独占权限
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                Log.e("instagram", "bitmap插入到media 插入成功")
            } catch (e: IOException) {
                Log.e("instagram", "bitmap插入到media 插入失败 $e")
                return null
            }
        } else {
            Log.e("instagram", "bitmap插入到media 插入失败")
        }
        return uri
    }

    /**
     * 根据URL下载图片
     */
    fun downloadImg(
        imageUrl: String?,
        connectTimeout: Int,
        readTimeout: Int
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
```
