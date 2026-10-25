package com.moorty.voice

import android.content.Context
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** TikTok LIVE adapter for the community TikTokLiveJava connector (unofficial public LIVE rooms). */
class TikTokConnector(
    private val context: Context,
    private val username: String,
    private val normalSound: () -> String?,
    private val subscriberSound: () -> String?,
    private val onStatus: (String) -> Unit
) {
    private val stopped = AtomicBoolean(false)
    @Volatile private var client: Any? = null
    private var worker: Thread? = null
    private val lastEvents = HashMap<String, Long>()

    fun start() {
        if (worker?.isAlive == true) return
        stopped.set(false)
        worker = thread(name = "Moorty-TikTok-$username", isDaemon = true) {
            try {
                onStatus("TikTok: conectando a @$username…")
                val klass = findTikTokClass() ?: throw IllegalStateException("No se encontró la librería TikTokLiveJava dentro del APK.")
                val factory = klass.methods.firstOrNull { it.name == "newClient" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
                    ?: throw IllegalStateException("La versión instalada de TikTokLiveJava no tiene newClient(String).")
                val builder = factory.invoke(null, username.removePrefix("@"))
                    ?: throw IllegalStateException("TikTokLiveJava no creó el cliente. Revisa el usuario del LIVE.")

                hook(builder, "onConnected") { onStatus("TikTok conectado a @$username") }
                hook(builder, "onError") { args -> onStatus("TikTok: ${eventText(args.lastOrNull(), "exception", "message").ifBlank { args.lastOrNull()?.toString().orEmpty().ifBlank { "error de conexión" } }}") }
                hook(builder, "onComment") { args ->
                    val e = args.lastOrNull(); val user = eventText(e, "user", "name", "profileName"); val msg = eventText(e, "text", "comment")
                    if (msg.isNotBlank() && accept("comment:$user:$msg", 2500)) speak("${user.ifBlank { "Alguien" }} dice: ${msg.take(220)}")
                }
                hook(builder, "onGift") { args ->
                    val e = args.lastOrNull(); val user = eventText(e, "user", "name", "profileName"); val gift = eventText(e, "gift", "name")
                    if (accept("gift:$user:$gift", 1500)) speak("¡${user.ifBlank { "Alguien" }} envió ${gift.ifBlank { "un regalo" }}! ¡Gracias por el apoyo!")
                }
                hook(builder, "onFollow") { args -> val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName"); if (accept("follow:$user", 30_000)) speak("¡${user.ifBlank { "Alguien" }} empezó a seguir el canal! Muchas gracias.") }
                hook(builder, "onShare") { args -> val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName"); if (accept("share:$user", 10_000)) speak("¡Gracias ${user.ifBlank { "amigo" }} por compartir el directo!") }
                hook(builder, "onJoin") { args ->
                    val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName")
                    val isSub = explicitSubscriber(e)
                    if (user.isNotBlank() && accept("join:$user", 45_000)) {
                        val sound = if (isSub == true) subscriberSound() else normalSound()
                        VoiceService.speak(context, "¡$user se unió al directo!", 1f, 1f, sound)
                    }
                }
                hook(builder, "onLike") { args -> val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName"); val count=eventText(e,"count","likeCount","total"); if (accept("like:$user", 5000)) speak(if(count.isBlank()) "¡Gracias ${user.ifBlank { "por acompañarnos" }} por los likes!" else "¡Gracias ${user.ifBlank { "por acompañarnos" }} por enviar $count likes!") }
                hook(builder, "onSubscribe") { args -> val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName"); speak("¡${user.ifBlank { "Alguien" }} se suscribió al canal! Muchas gracias.", subscriberSound()) }

                val connect = builder.javaClass.methods.firstOrNull { it.name == "buildAndConnect" && it.parameterCount == 0 }
                    ?: throw IllegalStateException("El conector no expone buildAndConnect(). Revisa la versión de la dependencia.")
                client = connect.invoke(builder)
                onStatus("TikTok: intentando conectar a @$username…")
                while (!stopped.get()) Thread.sleep(1000)
            } catch (_: InterruptedException) {
                // stop requested
            } catch (e: Throwable) {
                val root = generateSequence(e) { it.cause }.lastOrNull() ?: e
                val detail = root.message?.takeIf { it.isNotBlank() } ?: root.javaClass.simpleName
                onStatus("TikTok no pudo conectar: $detail")
            }
        }
    }

    fun stop() {
        stopped.set(true)
        try { client?.javaClass?.methods?.firstOrNull { it.name in listOf("disconnect", "stop", "close") && it.parameterCount == 0 }?.invoke(client) } catch (_: Exception) { }
        worker?.interrupt(); worker = null; client = null
        onStatus("TikTok desconectado")
    }

    private fun findTikTokClass(): Class<*>? {
        val names = listOf("com.github.jwdeveloper.tiktoklive.TikTokLive", "com.github.jwdeveloper.tiktoklive.client.TikTokLive", "com.github.jwdeveloper.tiktoklive.api.TikTokLive")
        for (name in names) try { return Class.forName(name) } catch (_: Exception) { }
        try {
            val dexClass = Class.forName("dalvik.system.DexFile")
            val dex = dexClass.getConstructor(String::class.java).newInstance(context.applicationInfo.sourceDir)
            val entries = dexClass.getMethod("entries").invoke(dex) as java.util.Enumeration<*>
            while (entries.hasMoreElements()) {
                val name = entries.nextElement() as? String ?: continue
                if (name.endsWith(".TikTokLive") && name.contains("tiktoklive", true)) try { return Class.forName(name) } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        return null
    }

    /**
     * Dynamic proxies must return a valid value for every primitive-returning method.
     * Returning null for Object.hashCode() (int) or another primitive callback is the
     * direct cause of: "Expected to unbox 'int' primitive type but was returned null".
     */
    private fun hook(builder: Any, methodName: String, callback: (Array<out Any?>) -> Unit) {
        val method = builder.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 1 } ?: return
        val iface = method.parameterTypes[0]
        if (!iface.isInterface) return
        val handler = InvocationHandler { proxy, called, args ->
            when (called.name) {
                "toString" -> "MoortyTikTokCallback(${iface.simpleName})"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> {
                    if (called.name in listOf("accept", "invoke", "call", "onEvent")) {
                        try { callback(args ?: emptyArray()) } catch (e: Exception) { onStatus("TikTok: error procesando evento: ${e.message ?: "desconocido"}") }
                    }
                    primitiveDefault(called.returnType)
                }
            }
        }
        val proxy = Proxy.newProxyInstance(iface.classLoader ?: javaClass.classLoader, arrayOf(iface), handler)
        method.invoke(builder, proxy)
    }

    private fun primitiveDefault(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private fun eventText(root: Any?, vararg path: String): String {
        var current: Any? = root
        for (part in path) {
            if (current == null) return ""
            val cap = part.replaceFirstChar { it.uppercase() }
            val getterNames = listOf("get$cap", "is$cap", part)
            current = try { current.javaClass.methods.firstOrNull { it.name in getterNames && it.parameterCount == 0 }?.invoke(current) } catch (_: Exception) { null }
        }
        return current?.toString()?.takeIf { it != "null" } ?: ""
    }

    private fun explicitSubscriber(event: Any?): Boolean? {
        val roots = listOf(event, try { event?.javaClass?.methods?.firstOrNull { it.name == "getUser" && it.parameterCount == 0 }?.invoke(event) } catch (_: Exception) { null })
        for (root in roots) {
            if (root == null) continue
            for (name in listOf("isSubscriber", "getSubscriber", "isSub", "getIsSubscriber")) {
                try { val value = root.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(root); if (value is Boolean) return value } catch (_: Exception) { }
            }
        }
        return null
    }

    private fun accept(key: String, cooldown: Long): Boolean = synchronized(lastEvents) {
        val now = System.currentTimeMillis(); val old = lastEvents[key] ?: 0L
        if (now - old < cooldown) false else { lastEvents[key] = now; true }
    }
    private fun speak(text: String, sound: String? = null) = VoiceService.speak(context, text, 1f, 1f, sound)
}
