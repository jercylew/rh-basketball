package com.ruihao.basketball

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File


class BorrowLogAdapter(context: Context, borrowRecordList: ArrayList<BorrowRecord>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var mRecordList: ArrayList<BorrowRecord>
    private var mClickRemoveListener: OnClickRemoveListener? = null
    private var mDbHelper: BasketballDBHelper = BasketballDBHelper(context)

    var context: Context
    private val mPhotoSavePath: String = Environment.getExternalStorageDirectory().path +
            "/RhBasketball/data/"
    init {
        this.context = context
        this.mRecordList = borrowRecordList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.borrow_record_item, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val record = mRecordList[position]

        val userPhotoUrl = "$mPhotoSavePath/${record.borrowerId}.jpg"
        if (File(userPhotoUrl).exists()) {
            val imgBitmap = BitmapFactory.decodeFile(userPhotoUrl)
            (holder as BorrowLogAdapter.ViewHolder).mIVPhoto.setImageBitmap(imgBitmap)
        }
        else {
            val imgBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.user_photo)
            (holder as BorrowLogAdapter.ViewHolder).mIVPhoto.setImageBitmap(imgBitmap)
        }

        val borrowerInfo: User = mDbHelper.getUser(record.borrowerId) ?: return

        holder.mTVName.text = borrowerInfo.name
        holder.mTVDateTime.text = record.createdTime
        holder.mTVType.text = if (record.type == 0 || record.type == 2) "借" else "还"
        if (File(record.captureImagePath).exists()) {
            val imgBitmap = BitmapFactory.decodeFile(record.captureImagePath)
            holder.mTVCaptureImage.setImageBitmap(imgBitmap)
        }
        holder.mTVCaptureImage.setOnClickListener{
            val builder = Dialog(context)
            builder.requestWindowFeature(Window.FEATURE_NO_TITLE)
            builder.window!!.setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )
            builder.setOnDismissListener {
                //nothing;
            }
            val imageView = ImageView(context)
            val imgBitmap = BitmapFactory.decodeFile(record.captureImagePath)
            imageView.setImageBitmap(imgBitmap)
//            PicassoTrustAll.getInstance(context)
//                .load(imageUrls.get(position))
//                .placeholder(R.drawable.vector_app_logo_profile)
//                .into(imageView)
            builder.addContentView(imageView, RelativeLayout.LayoutParams(550, 550))
            builder.show()
        }

        holder.itemView.setOnLongClickListener {
            val pop = PopupMenu(holder.itemView.context, it)
            pop.inflate(R.menu.borrow_record_item_menu)
            pop.setOnMenuItemClickListener {item->
                when(item.itemId)
                {
                    R.id.recordRemove -> {
                        Log.d("BorrowLog Adapter", "To remove record: $position")
                        removeAt(position)
                        if (mClickRemoveListener != null) {
                            mClickRemoveListener!!.onClick(position, record )
                        }
                    }
                    else -> {

                    }
                }
                true
            }
            pop.show()
            true
        }
    }

    override fun getItemCount(): Int {
        return mRecordList.size
    }

    fun removeAt(position: Int) {
        mRecordList.removeAt(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, mRecordList.size);
    }

    interface OnClickRemoveListener {
        fun onClick(position: Int, model: BorrowRecord)
    }


    fun setOnClickRemoveListener(onClickListener: OnClickRemoveListener) {
        this.mClickRemoveListener = onClickListener
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var mIVPhoto: ImageView
        var mTVName: TextView
        var mTVDateTime: TextView
        var mTVType: TextView
        var mTVCaptureImage: ImageView
        init {
            mIVPhoto = view.findViewById<View>(R.id.borrowerPhoto) as ImageView
            mTVName = view.findViewById<View>(R.id.borrowerName) as TextView
            mTVDateTime = view.findViewById<View>(R.id.createdTime) as TextView
            mTVType = view.findViewById<View>(R.id.recordType) as TextView
            mTVCaptureImage = view.findViewById<View>(R.id.captureImage) as ImageView
        }
    }
}