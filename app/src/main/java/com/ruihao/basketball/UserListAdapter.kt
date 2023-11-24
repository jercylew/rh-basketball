package com.ruihao.basketball

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class UserListAdapter(context: Context, usersList: ArrayList<User>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var mUsersList: ArrayList<User>
    private var mClickEditListener: OnClickEditListener? = null
    private var mClickRemoveListener: OnClickRemoveListener? = null

    var context: Context
    private val mPhotoSavePath: String = Environment.getExternalStorageDirectory().path +
            "/RhBasketball/data/"

    init {
        this.context = context
        this.mUsersList = usersList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val userInfo: User = mUsersList[position]

        val userPhotoUrl = "$mPhotoSavePath/${userInfo.id}.jpg"
        if (File(userPhotoUrl).exists()) {
            val imgBitmap = BitmapFactory.decodeFile(userPhotoUrl)
            (holder as UserListAdapter.ViewHolder).mIVPhoto.setImageBitmap(imgBitmap)
        }
        (holder as UserListAdapter.ViewHolder).mTVClassGrade.text = "${userInfo.gradeNo}${userInfo.classNo}"
        (holder as UserListAdapter.ViewHolder).mTVName.text = userInfo.name
        (holder as UserListAdapter.ViewHolder).mTVGender.text = userInfo.gender
        (holder as UserListAdapter.ViewHolder).mTVNumber.text = userInfo.barQRNo  //Usually student number used to make bar/QR code

        holder.itemView.setOnLongClickListener {
            val pop = PopupMenu(holder.itemView.context, it)
            pop.inflate(R.menu.user_list_item_menu)
            pop.setOnMenuItemClickListener {item->
                when(item.itemId)
                {
                    R.id.edit->{
                        Log.d("UserList Adapter", "To edit item: $position")
                        if (mClickEditListener != null) {
                            mClickEditListener!!.onClick(position, userInfo )
                        }
                    }
                    R.id.remove->{
                        Log.d("UserList Adapter", "To remove item: $position")
                        removeAt(position)
                        if (mClickRemoveListener != null) {
                            mClickRemoveListener!!.onClick(position, userInfo )
                        }
                    }
                }
                true
            }
            pop.show()
            true
        }
    }

    override fun getItemCount(): Int {
        return mUsersList.size
    }

    fun removeAt(position: Int) {
        mUsersList.removeAt(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, mUsersList.size);
    }

    interface OnClickEditListener {
        fun onClick(position: Int, model: User)
    }

    interface OnClickRemoveListener {
        fun onClick(position: Int, model: User)
    }

    fun setOnClickEditListener(onClickListener: OnClickEditListener) {
        this.mClickEditListener = onClickListener
    }

    fun setOnClickRemoveListener(onClickListener: OnClickRemoveListener) {
        this.mClickRemoveListener = onClickListener
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