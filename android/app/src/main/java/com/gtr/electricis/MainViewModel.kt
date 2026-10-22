package com.gtr.electricis

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.google.gson.JsonParser

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
    init { viewModelScope.launch { url=repo.savedUrl() } }
    val canManage get()=user?.role in listOf("admin","supervisor")
    private fun run(action:suspend ()->Unit) {
        if(busy) return
        busy=true; error=null
        viewModelScope.launch {
            try { action() }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                if(e is HttpException && e.code()==401) { repo.clear(); user=null; selected=null }
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
        users=repo.users(); projects=repo.projects(); dashboard=repo.dashboard()
        selected?.id?.let { id -> selected=projects.find { it.id==id }; tasks=repo.tasks(id); reports=repo.reports(id) }
    }
    fun login(email:String,password:String)=run { user=repo.connect(url,email,password); load() }
    fun refresh()=run { load() }
    fun logout()=run { repo.logout(); user=null; selected=null; projects=emptyList(); tasks=emptyList(); reports=emptyList() }
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
}
