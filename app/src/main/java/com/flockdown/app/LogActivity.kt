package com.flockdown.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val entries = LogStore.getAll(this)
        val recycler = findViewById<RecyclerView>(R.id.recyclerLog)
        val tvCount = findViewById<TextView>(R.id.tvLogCount)

        tvCount.text = "${entries.size} entries"
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = LogAdapter(entries)
    }

    class LogAdapter(private val entries: List<LogEntry>) :
        RecyclerView.Adapter<LogAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvLogTitle)
            val coords: TextView = view.findViewById(R.id.tvLogCoords)
            val time: TextView = view.findViewById(R.id.tvLogTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false))

        override fun getItemCount() = entries.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = entries[position]
            holder.title.text = if (entry.isFlock) "Flock Safety ALPR" else "ALPR Camera"
            holder.coords.text = "%.5f, %.5f".format(entry.lat, entry.lon)
            holder.time.text = LogStore.formatTime(entry.timestamp)
        }
    }
}
