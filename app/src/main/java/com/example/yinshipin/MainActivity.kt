package com.example.yinshipin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yinshipin.activity.CameraPreviewActivity
import com.permissionx.guolindev.PermissionX
import kotlinx.android.synthetic.main.activity_main.*
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {
    private var mDataList = mutableListOf<ItemDataBean>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PermissionX.init(this).permissions(android.Manifest.permission.CAMERA)
            .request { allGranted, grantedList, deniedList ->
                run {
                    if (deniedList.isNotEmpty()) {
                        finish()
                    }
                }
            }

        setContentView(R.layout.activity_main)
        initDataList()
        val llm = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerview.setLayoutManager(llm)
        val homeListAdapter = HomeListAdapter(this, mDataList)
        recyclerview.setAdapter(homeListAdapter)
    }

    private fun initDataList() {
        mDataList.add(
            ItemDataBean(
                "CameraPreview",
                CameraPreviewActivity::class.java, ColorGenerator.getInstance().getColor()
            )
        );
    }
}