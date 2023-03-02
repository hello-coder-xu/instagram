package com.example.instagram

import android.content.*
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.io.File


class MainActivity : AppCompatActivity() {
    private val context: Context = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val shareLocalImage: Button = findViewById(R.id.share_local_image)
        val shareNetworkImage: Button = findViewById(R.id.share_network_image)
        val shareResourceImage: Button = findViewById(R.id.share_resource_image)
        val shareMediaImage: Button = findViewById(R.id.share_media_image)
        shareNetworkImage.setOnClickListener {
            val networkImageUrl =
                "https://img2.591.com.tw/house/2022/07/06/165709842179541103.jpg!900x.water3.jpg"
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
        }
        shareResourceImage.setOnClickListener {
            // 资源图片
            InstagramShare.get().shareResource(context, R.drawable.instagram1, "instagram1.png")
        }
        shareMediaImage.setOnClickListener {
            // 媒体图片
            InstagramShare.get().shareMediaFile(context, "instagram1.png")
        }
        shareLocalImage.setOnClickListener {
            // 本地图片
            val fileName = "instagram.png"
            val folder = context.resources.getString(R.string.app_name)
            val externalCacheDirPath = externalCacheDir?.absoluteFile?.path
            val localFilePath =
                externalCacheDirPath + File.separator + folder + File.separator + fileName
            val mediaFileName = "localFile.png"
            InstagramShare.get().shareLocalFile(context, localFilePath, mediaFileName)
        }
    }

}