package com.gtr.electricis

import android.app.TimePickerDialog
import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

fun validTime(value:String)=Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$").matches(value)

@Composable fun TimeField(label:String,value:String,change:(String)->Unit) {
    val context=LocalContext.current
    OutlinedButton(onClick={
        val parts=value.split(":")
        TimePickerDialog(context,{_,h,m->change("%02d:%02d".format(h,m))},parts[0].toInt(),parts[1].toInt(),true).show()
    }) { Text("$label: $value") }
}

@Composable fun Countdown(iso:String) {
    var left by remember(iso) { mutableLongStateOf(minutesLeft(iso)) }
    LaunchedEffect(iso) { while(true) { left=minutesLeft(iso); delay(30_000) } }
    Text(if(left>0) "Faltan $left minutos para la entrega" else "Plazo vencido · ${-left} minutos de retraso",
        color=if(left<=180) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        fontWeight=FontWeight.Medium)
}

@Composable fun UsersScreen(vm:MainViewModel) {
    var form by remember { mutableStateOf(false) }
    Text("Administración de usuarios",style=MaterialTheme.typography.headlineSmall)
    Text("Crea las cuentas y define el acceso de cada integrante.")
    Button(onClick={form=true},enabled=!vm.busy) { Text("+ Crear usuario") }
    vm.users.forEach { Panel { Text(it.name,fontWeight=FontWeight.Bold); Text("${it.email} · ${it.role}") } }
    if(form) {
        var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }; var role by remember { mutableStateOf("tecnico") }
        FormDialog("Crear usuario",vm.busy,name.trim().length>=2 && email.contains("@") && password.length>=12,vm.error,{form=false},{vm.createUser(NewUser(name,email,password,role)) {form=false}}) {
            Field("Nombre completo",name) {name=it}; Field("Correo",email) {email=it}
            OutlinedTextField(value=password,onValueChange={password=it},label={Text("Contraseña · mínimo 12 caracteres")},visualTransformation=PasswordVisualTransformation())
            Choice("Rol",role,listOf("tecnico","supervisor","admin")) {role=it}
        }
    }
}

@Composable fun MailScreen(vm:MainViewModel) {
    var compose by remember { mutableStateOf(false) }; var request by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }; var opened by remember { mutableStateOf<Mail?>(null) }
    var replyTo by remember { mutableStateOf<Int?>(null) }
    Text("Mensajes del equipo",style=MaterialTheme.typography.headlineSmall)
    Text(if(vm.user?.role=="tecnico") "Comunícate con tu supervisor." else "Coordina el trabajo y las solicitudes del equipo.")
    Row { TextButton(onClick={sent=false}) { Text("Recibidos") }; TextButton(onClick={sent=true}) { Text("Enviados") }; TextButton(onClick={vm.refresh()},enabled=!vm.busy) {Text("Actualizar")} }
    Button(onClick={request=false; replyTo=null; compose=true},enabled=!vm.busy) { Text("+ Nuevo mensaje") }
    if(vm.user?.role=="supervisor") OutlinedButton(onClick={request=true; replyTo=null; compose=true},enabled=!vm.busy) { Text("Solicitar creación de usuario") }
    val mails=vm.mail.filter { if(sent) it.sender_id==vm.user!!.id else it.recipient_id==vm.user!!.id }
    if(mails.isEmpty()) Text("No tienes mensajes en esta bandeja.")
    mails.forEach { m ->
        Card(onClick={opened=m; if(!sent && m.read_at==null) vm.readMail(m.id)},modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text((if(!sent && m.read_at==null) "● " else "")+m.subject,fontWeight=FontWeight.Bold)
                Text(if(sent) "Para: ${vm.name(m.recipient_id)}" else "De: ${vm.name(m.sender_id)}")
                Text(m.body.take(100)); Text(m.created_at,style=MaterialTheme.typography.labelSmall)
            }
        }
    }
    opened?.let { m -> AlertDialog(onDismissRequest={opened=null},title={Text(m.subject)},text={Column(Modifier.verticalScroll(rememberScrollState())) {Text("De: ${vm.name(m.sender_id)}"); Text(m.body)}},confirmButton={TextButton(onClick={opened=null}) {Text("Cerrar")}},dismissButton={
        if(m.recipient_id==vm.user!!.id && vm.recipients.any {it.id==m.sender_id}) TextButton(onClick={replyTo=m.sender_id; request=false; opened=null; compose=true}) {Text("Responder")}
    }) }
    if(compose) {
        val choices=vm.recipients.filter { !request || it.role=="admin" }
        var recipient by remember { mutableIntStateOf(replyTo ?: choices.firstOrNull()?.id ?: 0) }
        var subject by remember { mutableStateOf(if(request) "Solicitud de nuevo usuario" else "") }
        var body by remember { mutableStateOf(if(request) "Nombre completo: \nCorreo: \nRol solicitado (técnico o supervisor): \nMotivo: " else "") }
        FormDialog(if(request) "Solicitar usuario al administrador" else "Nuevo mensaje",vm.busy,choices.any {it.id==recipient} && subject.isNotBlank() && body.isNotBlank(),vm.error,{compose=false},{vm.send(MailBody(recipient,subject,body)) {compose=false}}) {
            UserChoice("Destinatario",recipient,choices) {recipient=it}
            Field("Asunto",subject) {subject=it}; Field("Mensaje",body,true) {body=it}
            if(request) Text("El administrador revisará la solicitud y creará la cuenta desde Usuarios. No envíes contraseñas por aquí.")
            if(choices.isEmpty()) Text("No hay destinatarios disponibles para tu rol.")
        }
    }
}

@Composable fun PrintPreview(html:String,close:()->Unit) {
    val context=LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }; var ready by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }
    AlertDialog(onDismissRequest=close,title={Text("Informe completo")},text={
        AndroidView(factory={ctx->WebView(ctx).apply {
            settings.javaScriptEnabled=false; settings.allowFileAccess=false; settings.allowContentAccess=false
            webViewClient=object:WebViewClient() { override fun onPageFinished(view:WebView?,url:String?) {ready=true} }
            web=this; loadDataWithBaseURL(null,html,"text/html","UTF-8",null)
        }},modifier=Modifier.fillMaxWidth().height(420.dp))
    },confirmButton={Button(enabled=ready,onClick={
        web?.let { (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("Informe Electricis",it.createPrintDocumentAdapter("Informe Electricis"),null) }
    }) {Text("Imprimir / PDF")}},dismissButton={TextButton(onClick=close) {Text("Cerrar")}})
}
