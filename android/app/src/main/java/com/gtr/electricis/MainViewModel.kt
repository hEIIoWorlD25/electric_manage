package com.gtr.electricis

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import com.google.gson.JsonParser
import java.io.File

class MainViewModel(application:Application):AndroidViewModel(application) {
    private val repo=Repository(application)
    var url by mutableStateOf("https://plumbing-baghdad-warrant-impaired.trycloudflare.com/")
    var user by mutableStateOf<User?>(null); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var projects by mutableStateOf(emptyList<Project>()); private set
    var users by mutableStateOf(emptyList<User>()); private set
    var tasks by mutableStateOf(emptyList<Task>()); private set
    var reports by mutableStateOf(emptyList<Report>()); private set
    var history by mutableStateOf<List<History>?>(null)
    var selected by mutableStateOf<Project?>(null); private set
    var dashboard by mutableStateOf(Dashboard()); private set
    var field by mutableStateOf<FieldDetails?>(null); private set
    var fieldTask by mutableStateOf<Task?>(null); private set
    var fieldPhoto by mutableStateOf<Bitmap?>(null); private set
    var reminders by mutableStateOf(emptyList<ReminderItem>()); private set
    init { viewModelScope.launch { url=repo.savedUrl() } }
    var mail by mutableStateOf(emptyList<Mail>()); private set
    var recipients by mutableStateOf(emptyList<User>()); private set
    var printHtml by mutableStateOf<String?>(null)
    val isAdmin get()=user?.role=="admin"
    fun createUser(body:NewUser,done:()->Unit)=run { repo.createUser(body); done(); load() }
    fun send(body:MailBody,done:()->Unit)=run { repo.send(body); done(); load() }
    fun readMail(id:Int)=run { repo.read(id); mail=repo.messages() }
    fun printProject()=run { printHtml=repo.printProject(selected!!.id) }
    val canManage get()=user?.role in listOf("admin","supervisor")
    private fun run(action:suspend ()->Unit) {
        if(busy) return
        busy=true; error=null
        viewModelScope.launch {
            try { action() }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                if(e is HttpException && e.code()==401 && user!=null) { repo.clear(); clearSession() }
                error=when(e) {
                    is HttpException -> runCatching {
                        val detail=JsonParser.parseString(e.response()?.errorBody()?.string()).asJsonObject.get("detail")
                        if(detail.isJsonPrimitive) detail.asString else "Revisa los campos, las fechas y los valores ingresados."
                    }.getOrDefault("Error HTTP ${e.code()}")
                    is java.io.IOException -> "No se pudo conectar. Revisa la URL, la red y que la API esté encendida."
                    else -> e.message ?: "No se pudo completar la operación"
                }
            } finally { busy=false }
        }
    }
    private suspend fun load() {
        mail=repo.messages(); recipients=repo.recipients()
        users=repo.users(); projects=repo.projects(); dashboard=repo.dashboard()
        selected?.id?.let { id -> selected=projects.find { it.id==id }; tasks=repo.tasks(id); reports=repo.reports(id) }
        val account=user ?: return
        reminders=repo.reminders()
        DeadlineReminders.update(getApplication(),"${repo.savedUrl()}:${account.id}",reminders)
    }
    fun login(email:String,password:String)=run {
        val account=repo.connect(url,email,password)
        DeadlineReminders.selectAccount(getApplication(),"${repo.savedUrl()}:${account.id}")
        user=account; load()
    }
    fun refresh()=run { load() }
    private fun clearSession() {
        DeadlineReminders.clear(getApplication())
        user=null; selected=null; projects=emptyList(); tasks=emptyList(); reports=emptyList()
        reminders=emptyList(); mail=emptyList(); recipients=emptyList(); printHtml=null; history=null; closeField()
    }
    fun logout()=run { repo.logout(); clearSession() }
    fun open(project:Project)=run {
        val newTasks=repo.tasks(project.id); val newReports=repo.reports(project.id)
        selected=project; tasks=newTasks; reports=newReports
    }
    fun back() { selected=null }
    fun saveProject(id:Int?,body:ProjectBody,done:()->Unit)=run { repo.project(id,body); done(); load() }
    fun saveTask(id:Int?,body:TaskBody,done:()->Unit)=run { repo.task(selected!!.id,id,body); done(); load() }
    fun status(task:Task,status:String)=run { repo.status(task.id,status); load() }
    fun history(task:Task)=run { history=repo.history(task.id) }
    fun saveReport(body:ReportBody,done:()->Unit)=run { repo.report(selected!!.id,body); done(); load() }
    fun name(id:Int)=users.find { it.id==id }?.name ?: "Usuario #$id"

    fun openField(task:Task)=run {
        val details=repo.field(task.id)
        fieldTask=task; field=details; fieldPhoto=null
    }
    fun closeField() { fieldTask=null; field=null; fieldPhoto=null }
    fun setChecklist(id:Int,items:List<String>)=run { field=repo.checklist(id,items); load() }
    fun startField(id:Int)=run {
        field=repo.startField(id,captureStartLocation(getApplication())); load()
    }
    fun answerChecklist(id:Int,index:Int,checked:Boolean)=run {
        val current=field ?: return@run
        if(current.task_id==id) field=repo.answers(id,current.checklist.mapIndexed { i,item -> if(i==index) checked else item.checked })
    }
    fun attachPhoto(id:Int,uri:Uri,temporary:File?=null) {
        if(busy || field?.task_id!=id) { temporary?.delete(); return }
        run {
            try {
                val jpeg=prepareEvidence(getApplication(),uri)
                field=repo.uploadPhoto(id,jpeg)
                fieldPhoto=withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(jpeg,0,jpeg.size) }
            } finally { temporary?.delete() }
        }
    }
    fun viewPhoto(id:Int)=run {
        val bytes=repo.photo(id)
        fieldPhoto=withContext(Dispatchers.IO) {
            checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size)) { "No se pudo mostrar la foto." }
        }
    }
    fun finishField(id:Int)=run { field=repo.finishField(id); load() }
}
