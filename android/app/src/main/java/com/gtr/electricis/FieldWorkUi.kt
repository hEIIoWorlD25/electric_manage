package com.gtr.electricis

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private fun limaTime(value:String):String=runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("America/Lima"))
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
}.getOrDefault(value)

@Composable
fun FieldWorkDialog(vm:MainViewModel, task:Task, details:FieldDetails) {
    val context=LocalContext.current
    val projectOpen=vm.selected?.status!="finalizado"
    val canEditChecklist=vm.canManage && projectOpen && details.started_at==null && details.status!="completada"
    val assigned=vm.user?.id==task.assignee_id
    val inProgress=assigned && projectOpen && details.started_at!=null &&
        details.finished_at==null && details.status=="en_proceso"
    var checklist by rememberSaveable(task.id) { mutableStateOf(details.checklist.joinToString("\n") { it.label }) }
    val labels=checklist.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val validChecklist=labels.size<=30 && labels.all { it.length<=160 }
    var cameraPath by rememberSaveable(task.id) { mutableStateOf<String?>(null) }
    val location=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(canReadLocation(context)) vm.startField(task.id)
        else vm.error="Concede el permiso de ubicación para registrar el inicio."
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) vm.attachPhoto(task.id,uri)
    }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file=cameraPath?.let(::File)
        cameraPath=null
        if(taken && file!=null) {
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.evidence",file)
            vm.attachPhoto(task.id,uri,file)
        } else file?.delete()
    }
    val waitingForCamera=cameraPath!=null

    AlertDialog(
        onDismissRequest={if(!vm.busy && !waitingForCamera) vm.closeField()},
        title={Text("Trabajo en campo")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(task.title,fontWeight=FontWeight.Bold)
                Text("Responsable: ${vm.name(task.assignee_id)}")
                if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                if(canEditChecklist) {
                    Field("Checklist (un paso por línea)",checklist,true) { checklist=it }
                    if(!validChecklist) Text("Máximo 30 pasos de 160 caracteres.",color=MaterialTheme.colorScheme.error)
                    Button(onClick={vm.setChecklist(task.id,labels)},enabled=validChecklist && !vm.busy &&
                        labels!=details.checklist.map { it.label }) { Text("Enviar checklist") }
                }
                if(details.started_at==null) {
                    if(assigned && projectOpen && details.status!="completada") {
                        Text("Al marcar el inicio se guarda la hora y la ubicación de este momento.")
                        Button(onClick={
                            if(canReadLocation(context)) vm.startField(task.id)
                            else location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
                        },enabled=!vm.busy) { Text("Registrar inicio") }
                    } else Text("Sin inicio en campo registrado.")
                } else {
                    Text("Inicio: ${limaTime(details.started_at)} (hora de Perú)")
                    details.started_by?.let { Text("Registrado por: ${vm.name(it)}") }
                    val lat=details.latitude
                    val lon=details.longitude
                    if(lat!=null && lon!=null) {
                        val coordinates=String.format(Locale.US,"%.6f,%.6f",lat,lon)
                        Text("Ubicación de inicio: $coordinates")
                        details.accuracy_m?.let { Text("Precisión estimada: ±${it.roundToInt()} m") }
                        TextButton(onClick={
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:$coordinates?q=$coordinates")))
                            } catch (_:ActivityNotFoundException) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://www.google.com/maps/search/?api=1&query=$coordinates")))
                                } catch (_:ActivityNotFoundException) {
                                    vm.error="No hay una aplicación disponible para abrir el mapa."
                                }
                            }
                        }) { Text("Ver ubicación en el mapa") }
                    }
                    details.finished_at?.let { Text("Fin: ${limaTime(it)} (hora de Perú)") }
                }

                Text("Checklist asignado",fontWeight=FontWeight.Bold)
                if(details.checklist.isEmpty()) Text("Sin pasos asignados.")
                details.checklist.forEachIndexed { index,item ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(item.checked,onCheckedChange={vm.answerChecklist(task.id,index,it)},
                            enabled=inProgress && !vm.busy)
                        Text(item.label)
                    }
                }
                Text("Fotografía del trabajo",fontWeight=FontWeight.Bold)
                if(details.has_photo) {
                    val photo=vm.fieldPhoto
                    if(photo!=null) Image(photo.asImageBitmap(),"Fotografía del trabajo",
                        Modifier.fillMaxWidth().heightIn(max=250.dp))
                    else OutlinedButton(onClick={vm.viewPhoto(task.id)},enabled=!vm.busy) { Text("Ver foto") }
                } else Text("Sin foto adjunta.")

                if(inProgress) {
                    OutlinedButton(onClick={
                        try {
                            val directory=File(context.cacheDir,"field-capture").apply { mkdirs() }
                            directory.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>86_400_000L }
                                ?.forEach { it.delete() }
                            val file=File.createTempFile("evidence-", ".jpg", directory)
                            cameraPath=file.absolutePath
                            camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.evidence",file))
                        } catch (_:Exception) {
                            cameraPath?.let { File(it).delete() }
                            cameraPath=null
                            vm.error="No se pudo abrir la cámara. Puedes adjuntar una imagen."
                        }
                    },enabled=!vm.busy && !waitingForCamera) { Text("Tomar foto") }
                    OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},
                        enabled=!vm.busy && !waitingForCamera) { Text(if(details.has_photo) "Cambiar foto" else "Adjuntar foto") }
                    Button(onClick={vm.finishField(task.id)},enabled=!vm.busy && !waitingForCamera &&
                        details.has_photo && details.checklist.all { it.checked }) { Text("Finalizar trabajo") }
                    if(!details.has_photo || details.checklist.any { !it.checked })
                        Text("Completa los pasos asignados y adjunta una foto para finalizar.")
                }
            }
        },
        confirmButton={TextButton(onClick={vm.closeField()},enabled=!vm.busy && !waitingForCamera) { Text("Cerrar") }}
    )
}

@Composable
fun DeadlinePanel(items:List<ReminderItem>) {
    val context=LocalContext.current
    var enabled by remember { mutableStateOf(DeadlineReminders.isEnabled(context)) }
    val focused=LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if(focused) {
            enabled=DeadlineReminders.isEnabled(context)
            if(enabled) DeadlineReminders.check(context)
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled=DeadlineReminders.isEnabled(context)
        if(enabled) DeadlineReminders.check(context)
    }
    val today=limaToday()
    val soon=items.count { deadlineState(it.due_date,today)=="por_vencer" }
    val late=items.count { deadlineState(it.due_date,today)=="retrasada" }
    Panel {
        Text("Avisos de actividades",fontWeight=FontWeight.Bold)
        Text("$soon por vencer · $late retrasadas")
        Text("Por vencer: hoy o mañana. Actualiza para revisar las fechas más recientes.",
            style=MaterialTheme.typography.bodySmall)
        if(!enabled) {
            OutlinedButton(onClick={
                if(Build.VERSION.SDK_INT>=33 &&
                    ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
            }) { Text("Activar notificaciones") }
            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}) { Text("Ajustes de notificaciones") }
        }
    }
}
