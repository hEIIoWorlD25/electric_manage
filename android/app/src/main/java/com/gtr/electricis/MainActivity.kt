package com.gtr.electricis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF075E69),secondary=Color(0xFFD88A12),background=Color(0xFFF3F6FA),surface=Color.White)) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) { Electricis() }
            }
        }
    }
}
@Composable fun Electricis(vm:MainViewModel=viewModel()) {
    var projectForm by remember { mutableStateOf(false) }
    var taskForm by remember { mutableStateOf(false) }
    var reportForm by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    BackHandler(vm.selected!=null && !vm.busy) { vm.back() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Column { Text("ELECTRICIS",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary); Text("MANAGER · GTR S.A.C.",style=MaterialTheme.typography.labelSmall) }
            if(vm.user!=null) TextButton(onClick={vm.logout()},enabled=!vm.busy) { Text("Salir") }
        }
        if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { message ->
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer),modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                Text(message,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick={vm.error=null}) { Text("Cerrar aviso") }
            }
        }
        if(vm.user==null) LoginScreen(vm)
        else Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${vm.user!!.name} · ${vm.user!!.role}",style=MaterialTheme.typography.labelLarge)
            if(vm.selected==null) {
                Text("Vista general",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                val d=vm.dashboard
                Panel {
                    Text("AVANCE GENERAL",style=MaterialTheme.typography.labelMedium)
                    Text("${d.progress}%",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
                    LinearProgressIndicator(progress={d.progress/100f},modifier=Modifier.fillMaxWidth())
                    Text("${d.active_projects} proyectos activos · ${d.finished_projects} finalizados")
                    Text("${d.pending_tasks} tareas pendientes · ${d.completed_tasks} completadas")
                    Text("${d.overdue_tasks} tareas retrasadas",color=MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Proyectos",style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={vm.refresh()},enabled=!vm.busy) { Text("Actualizar") }
                }
                if(vm.canManage) Button(onClick={editingProject=null; projectForm=true},enabled=!vm.busy) { Text("+ Nuevo proyecto") }
                if(vm.projects.isEmpty()) Text("Aún no hay proyectos registrados.")
                vm.projects.forEach { p ->
                    Card(onClick={if(!vm.busy) vm.open(p)},modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(p.status.uppercase(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            Text(p.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            Text(p.location); Text("Responsable: ${vm.name(p.manager_id)}")
                            LinearProgressIndicator(progress={p.progress/100f},modifier=Modifier.fillMaxWidth())
                            Text("${p.progress}% · ${p.completed_tasks}/${p.total_tasks} actividades")
                            Text("Entrega: ${p.end_date}",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                val p=vm.selected!!
                Row { TextButton(onClick={vm.back()},enabled=!vm.busy) { Text("‹ Proyectos") }; TextButton(onClick={vm.refresh()},enabled=!vm.busy) { Text("Actualizar") } }
                Text(p.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Panel {
                    Text(p.description); Text(p.location)
                    Text("${p.start_date} → ${p.end_date}")
                    Text("Responsable: ${vm.name(p.manager_id)}")
                    Text("${p.status} · Avance ${p.progress}%")
                    if(vm.canManage) TextButton(onClick={editingProject=p; projectForm=true},enabled=!vm.busy) { Text("Editar proyecto") }
                }
                Text("Actividades",style=MaterialTheme.typography.titleLarge)
                if(vm.canManage && p.status!="finalizado") Button(onClick={editingTask=null; taskForm=true},enabled=!vm.busy) { Text("+ Nueva actividad") }
                if(vm.tasks.isEmpty()) Text("Registra la primera actividad de este proyecto.")
                vm.tasks.forEach { t ->
                    Panel {
                        Text(t.title,fontWeight=FontWeight.Bold)
                        Text("${vm.name(t.assignee_id)} · Prioridad ${t.priority}")
                        Text("Fecha: ${t.due_date} · ${t.status.replace('_',' ')}")
                        if(t.status!="completada" && t.due_date<LocalDate.now().toString()) Text("Retrasada",color=MaterialTheme.colorScheme.error)
                        if(p.status!="finalizado" && (vm.canManage || vm.user!!.id==t.assignee_id)) {
                            Choice("Estado",t.status,listOf("pendiente","en_proceso","completada"),!vm.busy) { vm.status(t,it) }
                        }
                        Row {
                            if(vm.canManage && p.status!="finalizado") TextButton(onClick={editingTask=t; taskForm=true},enabled=!vm.busy) { Text("Editar") }
                            TextButton(onClick={vm.history(t)},enabled=!vm.busy) { Text("Historial") }
                        }
                    }
                }
                Text("Informes de campo",style=MaterialTheme.typography.titleLarge)
                Button(onClick={reportForm=true},enabled=!vm.busy) { Text("+ Registrar informe") }
                if(vm.reports.isEmpty()) Text("No hay informes registrados.")
                vm.reports.forEach { r -> Panel {
                    Text("${r.work_date} · ${vm.name(r.author_id)}",fontWeight=FontWeight.Bold)
                    Text("Trabajo realizado\n${r.progress}")
                    if(r.incidents.isNotBlank()) Text("Incidencias\n${r.incidents}")
                    if(r.observations.isNotBlank()) Text("Observaciones\n${r.observations}")
                } }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if(vm.user!=null && projectForm) ProjectForm(vm,editingProject) { projectForm=false }
    if(vm.user!=null && taskForm) TaskForm(vm,editingTask) { taskForm=false }
    if(vm.user!=null && reportForm) ReportForm(vm) { reportForm=false }
    vm.history?.let { entries -> AlertDialog(onDismissRequest={vm.history=null},title={Text("Historial de actividad")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(entries.isEmpty()) Text("Sin cambios registrados.")
        entries.forEach { Text("${it.created_at}\n${vm.name(it.user_id)}\n${it.action}") }
    }},confirmButton={TextButton(onClick={vm.history=null}) { Text("Cerrar") }}) }
}
@Composable fun LoginScreen(vm:MainViewModel) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Tus proyectos,\nen un solo lugar.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Text("Gestión y supervisión de trabajos eléctricos.")
        Field("Correo",email) { email=it }
        OutlinedTextField(value=password,onValueChange={password=it},label={Text("Contraseña")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true)
        Field("URL de la API",vm.url) { vm.url=it }
        Text("Emulador: http://10.0.2.2:8000/",style=MaterialTheme.typography.bodySmall)
        Button(onClick={vm.login(email,password)},enabled=!vm.busy && email.isNotBlank() && password.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Ingresar") }
        Text("Usa el usuario creado en el servidor. La sesión dura hasta 8 horas y se pierde al cerrar el proceso de la app.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun Panel(content:@Composable ColumnScope.()->Unit) {
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
@Composable fun Field(label:String,value:String,multiline:Boolean=false,onChange:(String)->Unit) {
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},singleLine=!multiline,minLines=if(multiline) 3 else 1,modifier=Modifier.fillMaxWidth())
}
@Composable fun Choice(label:String,value:String,values:List<String>,enabled:Boolean=true,onChange:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={expanded=true},enabled=enabled) { Text("$label: ${value.replace('_',' ')} ▾") }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { values.forEach { item -> DropdownMenuItem(text={Text(item.replace('_',' '))},onClick={onChange(item); expanded=false}) } }
    }
}
@Composable fun UserChoice(label:String,id:Int,users:List<User>,onChange:(Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={expanded=true}) { Text("$label: ${users.find { it.id==id }?.name ?: "Seleccionar"} ▾") }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { users.forEach { u -> DropdownMenuItem(text={Text("${u.name} (${u.role})")},onClick={onChange(u.id); expanded=false}) } }
    }
}
@Composable fun FormDialog(title:String,busy:Boolean,valid:Boolean,error:String?,close:()->Unit,save:()->Unit,content:@Composable ColumnScope.()->Unit) {
    AlertDialog(onDismissRequest={if(!busy) close()},title={Text(title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) { content(); error?.let { Text(it,color=MaterialTheme.colorScheme.error) } }},confirmButton={Button(onClick=save,enabled=valid && !busy) { Text(if(busy) "Guardando…" else "Guardar") }},dismissButton={TextButton(onClick=close,enabled=!busy) { Text("Cancelar") }})
}
private fun validDate(s:String)=runCatching { LocalDate.parse(s) }.isSuccess
@Composable fun ProjectForm(vm:MainViewModel,p:Project?,close:()->Unit) {
    var name by remember { mutableStateOf(p?.name ?: "") }; var description by remember { mutableStateOf(p?.description ?: "") }
    var location by remember { mutableStateOf(p?.location ?: "") }; var owner by remember { mutableIntStateOf(p?.manager_id ?: vm.user!!.id) }
    var start by remember { mutableStateOf(p?.start_date ?: LocalDate.now().toString()) }; var end by remember { mutableStateOf(p?.end_date ?: LocalDate.now().plusDays(30).toString()) }
    var status by remember { mutableStateOf(p?.status ?: "activo") }
    FormDialog(if(p==null) "Nuevo proyecto" else "Editar proyecto",vm.busy,name.trim().length>=3 && location.trim().length>=2 && validDate(start) && validDate(end) && start<=end,vm.error,close,{vm.saveProject(p?.id,ProjectBody(name,description,location,owner,start,end,status),close)}) {
        Field("Nombre",name) {name=it}; Field("Descripción",description,true) {description=it}; Field("Ubicación",location) {location=it}
        UserChoice("Responsable",owner,vm.users.filter { it.role!="tecnico" }) {owner=it}
        Field("Inicio (AAAA-MM-DD)",start) {start=it}; Field("Fin (AAAA-MM-DD)",end) {end=it}
        Choice("Estado",status,if(p==null) listOf("activo","pausado") else listOf("activo","pausado","finalizado")) {status=it}
        Text("Para finalizar, todas las actividades deben estar completadas.")
    }
}
@Composable fun TaskForm(vm:MainViewModel,t:Task?,close:()->Unit) {
    var title by remember { mutableStateOf(t?.title ?: "") }; var owner by remember { mutableIntStateOf(t?.assignee_id ?: vm.user!!.id) }
    var due by remember { mutableStateOf(t?.due_date ?: vm.selected!!.end_date) }; var priority by remember { mutableStateOf(t?.priority ?: "media") }
    FormDialog(if(t==null) "Nueva actividad" else "Editar actividad",vm.busy,title.trim().length>=3 && validDate(due),vm.error,close,{vm.saveTask(t?.id,TaskBody(title,owner,due,priority),close)}) {
        Field("Actividad",title) {title=it}; UserChoice("Responsable",owner,vm.users) {owner=it}
        Field("Fecha límite (AAAA-MM-DD)",due) {due=it}; Choice("Prioridad",priority,listOf("baja","media","alta")) {priority=it}
        Text("La fecha debe estar entre ${vm.selected!!.start_date} y ${vm.selected!!.end_date}.")
    }
}
@Composable fun ReportForm(vm:MainViewModel,close:()->Unit) {
    var day by remember { mutableStateOf(LocalDate.now().toString()) }; var progress by remember { mutableStateOf("") }
    var incidents by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    FormDialog("Informe de campo",vm.busy,progress.trim().length>=5 && validDate(day) && day<=LocalDate.now().toString(),vm.error,close,{vm.saveReport(ReportBody(day,progress,incidents,notes),close)}) {
        Field("Fecha (AAAA-MM-DD)",day) {day=it}; Field("Trabajo realizado y avances",progress,true) {progress=it}
        Field("Incidencias",incidents,true) {incidents=it}; Field("Observaciones",notes,true) {notes=it}
        Text("El responsable y la fecha de registro se asignan automáticamente. El informe no cambia el porcentaje de avance.")
    }
}
