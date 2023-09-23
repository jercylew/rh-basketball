package com.ruihao.basketball

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserListAdapter(context: Context, courseImg: ArrayList<*>, courseName: ArrayList<*>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var courseImg: ArrayList<*>
    var courseName: ArrayList<*>
    var context: Context

    // Constructor for initialization
    init {
        this.context = context
        this.courseImg = courseImg
        this.courseName = courseName
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Inflating the Layout(Instantiates list_item.xml
        // layout file into View object)
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)

        // Passing view to ViewHolder
        return ViewHolder(view)
    }

//    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
//        TODO("Not yet implemented")
//    }

    // Binding data to the into specified position
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // TypeCast Object to int type
        val res = courseImg[position] as Int

        (holder as UserListAdapter.ViewHolder).images.setImageResource(res)
        holder.text.text = courseName[position] as String
    }

    override fun getItemCount(): Int {
        // Returns number of items
        // currently available in Adapter
        return courseImg.size
    }

    // Initializing the Views
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var images: ImageView
        var text: TextView
        init {
            images = view.findViewById<View>(R.id.courseImg) as ImageView
            text = view.findViewById<View>(R.id.courseName) as TextView
        }
    }
}