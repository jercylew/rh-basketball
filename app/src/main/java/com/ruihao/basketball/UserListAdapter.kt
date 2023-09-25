package com.ruihao.basketball

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class UserListAdapter(context: Context, usersList: ArrayList<User>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var mUsersList: ArrayList<User>
    var context: Context
    private val mPhotoSavePath: String = Environment.getExternalStorageDirectory().path +
            "/RhBasketball/data/"

    // Constructor for initialization
    init {
        this.context = context
        this.mUsersList = usersList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Inflating the Layout(Instantiates list_item.xml
        // layout file into View object)
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)

        // Passing view to ViewHolder
        return ViewHolder(view)
    }

    // Binding data to the into specified position
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val userInfo: User = mUsersList[position]

        // Photo
        val userPhotoUrl = "$mPhotoSavePath/${userInfo.id}.jpg"
        if (File(userPhotoUrl).exists()) {
            val imgBitmap = BitmapFactory.decodeFile(userPhotoUrl)
            (holder as UserListAdapter.ViewHolder).mIVPhoto.setImageBitmap(imgBitmap)
        }
        (holder as UserListAdapter.ViewHolder).mTVClassGrade.text = userInfo.classGrade
        (holder as UserListAdapter.ViewHolder).mTVName.text = userInfo.name
        (holder as UserListAdapter.ViewHolder).mTVGender.text = userInfo.gender
        (holder as UserListAdapter.ViewHolder).mTVNumber.text = userInfo.barQRNo  //Usually student number used to make bar/QR code
    }

    override fun getItemCount(): Int {
        // Returns number of items
        // currently available in Adapter
        return mUsersList.size
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var mIVPhoto: ImageView
        var mTVName: TextView
        var mTVNumber: TextView
        var mTVGender: TextView
        var mTVClassGrade: TextView
        init {
            mIVPhoto = view.findViewById<View>(R.id.userPhoto) as ImageView
            mTVName = view.findViewById<View>(R.id.userName) as TextView
            mTVNumber = view.findViewById<View>(R.id.userNo) as TextView
            mTVGender = view.findViewById<View>(R.id.userGender) as TextView
            mTVClassGrade = view.findViewById<View>(R.id.userClassGrade) as TextView
        }
    }
}