package ar.nacho.lineasagrada.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ar.nacho.lineasagrada.CierreWorker
import ar.nacho.lineasagrada.Filter
import ar.nacho.lineasagrada.Fmt
import ar.nacho.lineasagrada.Library
import ar.nacho.lineasagrada.Media
import ar.nacho.lineasagrada.Perms
import ar.nacho.lineasagrada.Progress
import ar.nacho.lineasagrada.Repo

class MainActivity : ComponentActivity() {
    private val openEvent = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openEvent.value = intent?.getStringExtra(EXTRA_EVENT)
        setContent { LineaTheme { Root(openEvent) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_EVENT)?.let { openEvent.value = it }
    }

    companion object {
        const val EXTRA_EVENT = "evento"
    }
}

sealed interface Dest {
    data object Tabs : Dest
    data class Ev(val id: String) : Dest
    data class CityD(val cc: String, val city: String) : Dest
    data object NoPlace : Dest
    data class Review(val title: String, val key: String, val eventId: String?) : Dest
    data class View(val list: List<Media>, val index: Int) : Dest
}

fun openAppSettings(ctx: android.content.Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
fun Root(openEvent: MutableState<String?>) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Perms.hasMedia(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = Perms.hasMedia(ctx)
    }
    if (!granted) {
        Onboarding { launcher.launch(Perms.request()) }
        return
    }
    val repo = remember { Repo.get(ctx) }
    LaunchedEffect(Unit) { CierreWorker.schedule(ctx) }
    // Cada vez que volvés a la app, busca fotos nuevas.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) repo.start() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val lib by repo.library.collectAsState()
    val progress by repo.progress.collectAsState()
    val stack = remember { mutableStateListOf<Dest>(Dest.Tabs) }
    val push: (Dest) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(stack.size > 1) { pop() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(Filter.TODO) }

    LaunchedEffect(openEvent.value, lib) {
        val id = openEvent.value
        val l = lib
        if (id != null && l != null) {
            l.events.find { it.id == id }?.let { stack.add(Dest.Review("Limpiar · ${it.name}", "evento", it.id)) }
            openEvent.value = null
        }
    }

    val l = lib
    Box(Modifier.fillMaxSize().background(Tva.Black)) {
        if (l == null) {
            Loading(progress)
            return@Box
        }
        when (val d = stack.last()) {
            Dest.Tabs -> TabsScreen(l, progress, tab, { tab = it }, filter, { filter = it }, push)
            is Dest.Ev -> {
                val ev = l.events.find { it.id == d.id }
                if (ev == null) LaunchedEffect(d) { pop() } else EventScreen(l, ev, filter, push, pop)
            }
            is Dest.CityD -> CityScreen(l, d.cc, d.city, filter, push, pop)
            Dest.NoPlace -> NoPlaceScreen(l, filter, push, pop)
            is Dest.Review -> {
                val groups = if (d.eventId != null) {
                    l.events.find { it.id == d.eventId }?.let { l.eventCleanup(it) } ?: emptyList()
                } else {
                    l.cleanup.filter { it.key == d.key }
                }
                ReviewScreen(d.title, groups, l, d.eventId, push, pop)
            }
            is Dest.View -> Viewer(d.list, d.index, l, pop)
        }
    }
}

@Composable
fun TabsScreen(
    l: Library,
    progress: Progress,
    tab: Int,
    onTab: (Int) -> Unit,
    filter: Filter,
    onFilter: (Filter) -> Unit,
    push: (Dest) -> Unit,
) {
    val pending = l.cleanup.sumOf { it.items.size }
    Scaffold(
        containerColor = Tva.Black,
        topBar = { Header(l, progress, if (tab != 2) filter else null, onFilter) },
        bottomBar = {
            NavigationBar(containerColor = Tva.Black, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Tva.Black,
                    selectedTextColor = Tva.Orange,
                    indicatorColor = Tva.Orange,
                    unselectedIconColor = Tva.Gray,
                    unselectedTextColor = Tva.Gray,
                )
                NavigationBarItem(tab == 0, { onTab(0) }, { Icon(Icons.Filled.DateRange, null) }, label = { Label("Fecha", if (tab == 0) Tva.Orange else Tva.Gray) }, colors = colors)
                NavigationBarItem(tab == 1, { onTab(1) }, { Icon(Icons.Filled.Place, null) }, label = { Label("Lugar", if (tab == 1) Tva.Orange else Tva.Gray) }, colors = colors)
                NavigationBarItem(
                    tab == 2, { onTab(2) },
                    {
                        BadgedBox(badge = {
                            if (pending > 0 || l.closed.isNotEmpty()) Badge(containerColor = Tva.Red) {
                                Text(if (pending > 999) "999+" else Fmt.n(pending + l.closed.size), fontSize = 9.sp)
                            }
                        }) { Icon(Icons.Filled.Delete, null) }
                    },
                    label = { Label("Poda", if (tab == 2) Tva.Orange else Tva.Gray) },
                    colors = colors,
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> Timeline(l, filter, push)
                1 -> Places(l, filter, push)
                else -> CleanupHome(l, progress, push)
            }
        }
    }
}

@Composable
fun Header(l: Library, progress: Progress, filter: Filter?, onFilter: (Filter) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.background(Tva.Black).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LÍNEA", style = Tva.title.copy(color = Tva.Orange))
            Text(" SAGRADA", style = Tva.title)
            Spacer(Modifier.weight(1f))
            Stamp("Exp. ${Fmt.n(l.all.size)}", color = Tva.Gray)
        }
        if (progress.active) {
            Label(
                "Estado: ${progress.label}" + if (progress.total > 0) " · ${Fmt.n(progress.done)}/${Fmt.n(progress.total)}" else "",
                color = Tva.Orange,
                modifier = Modifier.padding(top = 4.dp),
            )
            LinearProgressIndicator(
                progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp),
                color = Tva.Orange,
                trackColor = Tva.Line,
            )
        } else {
            Label("Estado: al día", modifier = Modifier.padding(top = 4.dp))
        }
        if (Perms.partial(ctx)) {
            Banner("Acceso parcial: solo veo las fotos que elegiste. Para ordenar todo, dale «Permitir todo».", "Ajustes") { openAppSettings(ctx) }
        }
        if (!Perms.location(ctx)) {
            Banner("Sin permiso de ubicación de fotos: no puedo agrupar por lugar.", "Ajustes") { openAppSettings(ctx) }
        }
        if (filter != null) {
            LazyRow(Modifier.padding(top = 8.dp)) {
                items(Filter.entries) { f -> Chip(f.label, f == filter) { onFilter(f) } }
            }
        }
    }
}

@Composable
fun Onboarding(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Tva.Black).statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Stamp("Expediente N.º 001 · Estado: pendiente")
        Spacer(Modifier.height(16.dp))
        Text("LÍNEA", style = Tva.title.copy(fontSize = 40.sp, color = Tva.Orange))
        Text("SAGRADA", style = Tva.title.copy(fontSize = 40.sp))
        Spacer(Modifier.height(20.dp))
        listOf(
            "Ordena tu galería por fecha, por lugar y por evento (tus viajes, solos).",
            "Te propone qué podar: repetidas, movidas, capturas y comprobantes viejos.",
            "Nada se borra sin tu OK, y lo que borres queda 30 días en la papelera.",
            "La app no tiene acceso a internet: tus fotos no salen del celu.",
        ).forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 6.dp)) {
                Text("${i + 1}.", style = Tva.label.copy(color = Tva.Orange, fontSize = 14.sp), modifier = Modifier.padding(end = 10.dp))
                Text(s, style = Tva.body)
            }
        }
        Spacer(Modifier.height(24.dp))
        TvaButton("Dar acceso a la galería", modifier = Modifier.fillMaxWidth(), onClick = onGrant)
        Spacer(Modifier.height(10.dp))
        Label("Elegí «Permitir todo», no «Seleccionar fotos». Y aceptá la ubicación de las fotos.")
    }
}

@Composable
fun Loading(progress: Progress) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Stamp("Abriendo expediente")
        Spacer(Modifier.height(12.dp))
        Label(if (progress.active) progress.label else "Leyendo galería", color = Tva.Orange)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Tva.Orange, trackColor = Tva.Line)
    }
}
