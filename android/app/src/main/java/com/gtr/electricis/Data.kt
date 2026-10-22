package com.gtr.electricis

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class User(val id:Int, val name:String, val email:String, val role:String)
data class LoginRequest(val email:String,val password:String)
data class LoginResponse(val token:String,val user:User)
data class Project(val id:Int,val name:String,val description:String,val location:String,val manager_id:Int,val start_date:String,val end_date:String,val status:String,val progress:Int,val total_tasks:Int,val completed_tasks:Int)
data class ProjectBody(val name:String,val description:String,val location:String,val manager_id:Int,val start_date:String,val end_date:String,val status:String)
data class Task(val id:Int,val project_id:Int,val title:String,val assignee_id:Int,val due_date:String,val priority:String,val status:String)
data class TaskBody(val title:String,val assignee_id:Int,val due_date:String,val priority:String)
data class StatusBody(val status:String)
data class Report(val id:Int,val project_id:Int,val author_id:Int,val work_date:String,val progress:String,val incidents:String,val observations:String,val created_at:String)
data class ReportBody(val work_date:String,val progress:String,val incidents:String,val observations:String)
data class History(val id:Int,val task_id:Int,val user_id:Int,val action:String,val created_at:String)
data class Dashboard(val active_projects:Int=0,val finished_projects:Int=0,val pending_tasks:Int=0,val completed_tasks:Int=0,val overdue_tasks:Int=0,val progress:Int=0)
interface Api {
    @POST("auth/login") suspend fun login(@Body body:LoginRequest):LoginResponse
    @POST("auth/logout") suspend fun logout(@Header("Authorization") auth:String):Map<String,String>
    @GET("users") suspend fun users(@Header("Authorization") auth:String):List<User>
    @GET("dashboard") suspend fun dashboard(@Header("Authorization") auth:String):Dashboard
    @GET("projects") suspend fun projects(@Header("Authorization") auth:String):List<Project>
    @POST("projects") suspend fun createProject(@Header("Authorization") auth:String,@Body body:ProjectBody):Project
    @PUT("projects/{id}") suspend fun updateProject(@Header("Authorization") auth:String,@Path("id") id:Int,@Body body:ProjectBody):Project
    @GET("projects/{id}/tasks") suspend fun tasks(@Header("Authorization") auth:String,@Path("id") id:Int):List<Task>
    @POST("projects/{id}/tasks") suspend fun createTask(@Header("Authorization") auth:String,@Path("id") id:Int,@Body body:TaskBody):Task
    @PUT("tasks/{id}") suspend fun updateTask(@Header("Authorization") auth:String,@Path("id") id:Int,@Body body:TaskBody):Task
    @PATCH("tasks/{id}/status") suspend fun status(@Header("Authorization") auth:String,@Path("id") id:Int,@Body body:StatusBody):Task
    @GET("tasks/{id}/history") suspend fun history(@Header("Authorization") auth:String,@Path("id") id:Int):List<History>
    @GET("projects/{id}/reports") suspend fun reports(@Header("Authorization") auth:String,@Path("id") id:Int):List<Report>
    @POST("projects/{id}/reports") suspend fun report(@Header("Authorization") auth:String,@Path("id") id:Int,@Body body:ReportBody):Report
}
private val Context.preferences by preferencesDataStore("settings")
class Repository(private val context:Context) {
    private val urlKey=stringPreferencesKey("api_url")
    private var token=""
    private lateinit var api:Api
    suspend fun savedUrl()=context.preferences.data.first()[urlKey] ?: "https://plumbing-baghdad-warrant-impaired.trycloudflare.com/"
    suspend fun connect(url:String,email:String,password:String):User {
        val clean=url.trim().trimEnd('/')+"/"
        require(clean.startsWith("http://") || clean.startsWith("https://")) { "La URL debe empezar con http:// o https://" }
        api=Retrofit.Builder().baseUrl(clean).addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
        val response=api.login(LoginRequest(email.trim(),password))
        token=response.token
        context.preferences.edit { it[urlKey]=clean }
        return response.user
    }
    private fun auth()="Bearer $token"
    fun clear() { token="" }
    suspend fun logout() { api.logout(auth()); clear() }
    suspend fun users()=api.users(auth())
    suspend fun dashboard()=api.dashboard(auth())
    suspend fun projects()=api.projects(auth())
    suspend fun tasks(id:Int)=api.tasks(auth(),id)
    suspend fun reports(id:Int)=api.reports(auth(),id)
    suspend fun history(id:Int)=api.history(auth(),id)
    suspend fun project(id:Int?,body:ProjectBody)=if(id==null) api.createProject(auth(),body) else api.updateProject(auth(),id,body)
    suspend fun task(project:Int,id:Int?,body:TaskBody)=if(id==null) api.createTask(auth(),project,body) else api.updateTask(auth(),id,body)
    suspend fun status(id:Int,status:String)=api.status(auth(),id,StatusBody(status))
    suspend fun report(project:Int,body:ReportBody)=api.report(auth(),project,body)
}
