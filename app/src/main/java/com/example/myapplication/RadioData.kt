package com.example.myapplication

import PodcastEpisode
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaZoneId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class RadioData() {
    private var documentFile: DocumentFile? = null
    private var item: PodcastEpisode? = null
    private var date: ZonedDateTime? = null

    constructor(documentFile: DocumentFile):this(){
        this.documentFile = documentFile
        val regex = "(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})".toRegex()
        val txt = documentFile.uri.path
        if(txt!=null) {
            val result: MatchResult? = regex.find(txt)
            if (result != null) {
                if (result.groupValues.isNotEmpty()) {
                    val y = result.groupValues[1]
                    val mm = result.groupValues[2]
                    val d = result.groupValues[3]
                    val h = result.groupValues[4]
                    val m = result.groupValues[5]
                    val s = result.groupValues[6]
                    val dstr = "%s-%s-%sT%s:%s:%s".format(y,mm,d,h,m,s)
                    //val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    val ldt = LocalDateTime.parse(dstr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    date = ldt.atZone(ZoneId.systemDefault())
                }
            }
        }
        if(date==null){
            val milli = documentFile.lastModified()
            date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(milli), ZoneId.systemDefault())
            //LocalDateTime.ofEpochSecond(milli/1000, 0, TimeZone.currentSystemDefault().)
        }
    }
    constructor(item:PodcastEpisode):this(){
        this.item = item
        // Thu, 14 Nov 2024 17:50:00 -0000
        val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z")
        date = ZonedDateTime.parse(item.pubDate, formatter)
    }

    fun getSaveFile(): String {
        if(documentFile!=null){
            return documentFile!!.uri.path.toString()
        }
        if(item!=null){
            return item!!.url
        }
        return ""
    }

    fun getName(): String {
        if(documentFile!=null){
            return documentFile!!.name.toString()
        }
        if(item!=null){
            return item!!.title.toString()
        }
        return ""
    }

    fun uri(): Uri? {
        if (documentFile!=null){
            return documentFile!!.uri
        }
        return null
    }

    fun url(): String? {
        if (item!=null){
            return item!!.url
        }
        return null
    }

    fun time(): Long {
        if(date!=null){
            return date!!.toInstant().toEpochMilli()
        }
        return 0
    }

    fun isSaveFile(saveFile:String): Boolean{
        return getSaveFile() == saveFile
    }
}
