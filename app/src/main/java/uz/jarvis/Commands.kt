package uz.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import java.util.Calendar

internal val TR = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
    'ж' to "j", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
    'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
    'ф' to "f", 'х' to "x", 'ц' to "s", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "",
    'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o", 'қ' to "q",
    'ғ' to "g", 'ҳ' to "h"
)

/** Kichik harf, kirillni lotinga, apostroflarsiz: "Qo'ng'iroq" -> "qongiroq" */
fun norm(s: String): String {
    val sb = StringBuilder()
    for (c in s.lowercase()) sb.append(TR[c] ?: c.toString())
    return sb.toString()
        .replace(Regex("[ʻʼ’‘`'´]"), "")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

class Commands(private val ctx: Context) {

    fun handle(raw: String): String {
        val t = norm(raw)
        if (t.isBlank()) return "Eshitmadim."
        val w = t.split(" ")
        return when {
            has(w, "xabar", "habar", "sms", "xat") -> lastMessage(t)
            has(w, "qongiroq", "tel", "telefon", "zvonok") -> call(w)
            has(w, "och", "ochib", "ochgin") || t.contains("ishga tushir") -> openApp(w)
            t.contains("soat") && t.contains("necha") -> time()
            t.contains("sana") || t.contains("nechanchi") || t.contains("qaysi kun") -> date()
            else -> "Tushunmadim. Masalan: Telegramdan oxirgi xabar kim yozdi. Dadamga qoʻngʻiroq qil. YouTube ni och. Soat necha."
        }
    }

    private fun has(words: List<String>, vararg keys: String) = words.any { it in keys }

    private fun granted(p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    // ---------- Xabarlar ----------
    private val appFilters = listOf(
        listOf("telegram") to listOf("org.telegram", "org.thunderdog"),
        listOf("whatsapp", "vatsap", "vatsapp", "watsap") to listOf("com.whatsapp"),
        listOf("messenjer", "messenger", "facebook", "fb") to listOf("com.facebook.orca"),
        listOf("instagram") to listOf("com.instagram"),
        listOf("sms") to listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging")
    )

    private fun appName(pkg: String) = when {
        pkg.startsWith("org.telegram") || pkg.startsWith("org.thunderdog") -> "Telegram"
        pkg.startsWith("com.whatsapp") -> "WhatsApp"
        pkg == "com.facebook.orca" -> "Messenger"
        pkg.startsWith("com.instagram") -> "Instagram"
        else -> "SMS"
    }

    private fun ago(time: Long): String {
        val m = (System.currentTimeMillis() - time) / 60000
        return when {
            m < 1 -> "hozirgina"
            m < 60 -> "$m daqiqa oldin"
            m < 1440 -> "${m / 60} soat oldin"
            else -> "${m / 1440} kun oldin"
        }
    }

    private fun lastMessage(t: String): String {
        var list = NotifStore.all(ctx)
        for ((keys, pkgs) in appFilters) {
            if (keys.any { t.contains(it) }) {
                list = list.filter { item -> pkgs.any { item.pkg.startsWith(it) } }
                break
            }
        }
        val last = list.lastOrNull()
        if (last == null) {
            val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            return if (enabled == null || !enabled.contains(ctx.packageName))
                "Bildirishnomalarga ruxsat berilmagan. Pastdagi tugma orqali ruxsat bering."
            else
                "Saqlangan xabar yoʻq. Faqat Jarvis ruxsat olgandan keyin kelgan xabarlar koʻrinadi."
        }
        return "Oxirgi xabar. ${appName(last.pkg)}, ${last.title}: ${last.text}. ${ago(last.time)}."
    }

    // ---------- Qoʻngʻiroq ----------
    private fun call(w: List<String>): String {
        val stop = setOf(
            "qongiroq", "tel", "telefon", "zvonok", "qil", "qilib", "ber", "iltimos", "menga",
            "jarvis", "hozir", "qilgin", "qilsang", "bilan", "gaplashay"
        )
        val q = w.filter { it !in stop }.joinToString("")
        if (q.isBlank()) return "Kimga qoʻngʻiroq qilay? Ismini ayting."
        if (!granted(Manifest.permission.READ_CONTACTS))
            return "Kontaktlarga ruxsat bering. Pastdagi Ruxsatlar tugmasini bosing."
        val found = findContact(q) ?: return "Kontaktlarda ${w.filter { it !in stop }.joinToString(" ")} topilmadi."
        val number = found.second.replace(Regex("[^0-9+]"), "")
        val action = if (granted(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        start(Intent(action, Uri.parse("tel:$number")))
        return "Qoʻngʻiroq qilyapman: ${found.first}."
    }

    private fun findContact(q: String): Pair<String, String>? {
        var best: Pair<String, String>? = null
        var bestScore = 0
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ), null, null, null
        ) ?: return null
        cursor.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                val n = norm(name).replace(" ", "")
                if (n.length < 2) continue
                val score = when {
                    q == n -> 100 + n.length
                    q.startsWith(n) -> 50 + n.length
                    q.length >= 3 && n.startsWith(q) -> 30 + q.length
                    else -> 0
                }
                if (score > bestScore) {
                    bestScore = score
                    best = Pair(name, num)
                }
            }
        }
        return best
    }

    // ---------- Ilova ochish ----------
    private val aliases = mapOf(
        "vatsap" to listOf("whatsapp"), "vatsapp" to listOf("whatsapp"), "watsap" to listOf("whatsapp"),
        "yutub" to listOf("youtube"), "yutube" to listOf("youtube"),
        "kamera" to listOf("kamera", "camera"), "galereya" to listOf("galereya", "gallery"),
        "sozlamalar" to listOf("sozlamalar", "settings"), "sozlama" to listOf("sozlamalar", "settings"),
        "xarita" to listOf("maps", "xarita"), "xaritalar" to listOf("maps", "xarita"),
        "xrom" to listOf("chrome"), "gugl" to listOf("google"),
        "kalkulator" to listOf("kalkulator", "calculator"), "soatlar" to listOf("soat", "clock")
    )

    private fun openApp(w: List<String>): String {
        val stop = setOf(
            "och", "ochib", "ochgin", "ber", "iltimos", "menga", "jarvis", "ilovani", "ilova",
            "ilovasini", "ishga", "tushir", "tushirib"
        )
        val q = w.filter { it !in stop }.joinToString("")
        if (q.isBlank()) return "Qaysi ilovani ochay?"
        val variants = mutableListOf(q)
        for ((k, v) in aliases) if (q.startsWith(k)) variants.addAll(v)

        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var bestPkg: String? = null
        var bestLabel = ""
        var bestScore = 0
        for (ri in pm.queryIntentActivities(main, 0)) {
            val label = ri.loadLabel(pm).toString()
            val n = norm(label).replace(" ", "")
            if (n.length < 2) continue
            for (v in variants) {
                val score = when {
                    v == n -> 100
                    n.length >= 3 && v.startsWith(n) -> 60 + n.length
                    v.length >= 3 && n.startsWith(v) -> 40 + v.length
                    else -> 0
                }
                if (score > bestScore) {
                    bestScore = score
                    bestPkg = ri.activityInfo.packageName
                    bestLabel = label
                }
            }
        }
        val pkg = bestPkg ?: return "Bunday ilova topilmadi."
        val launch = pm.getLaunchIntentForPackage(pkg) ?: return "$bestLabel ni ocha olmadim."
        start(launch)
        return "$bestLabel ochilyapti."
    }

    // ---------- Vaqt ----------
    private fun time(): String {
        val c = Calendar.getInstance()
        return "Soat %02d:%02d.".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun date(): String {
        val c = Calendar.getInstance()
        val days = listOf("yakshanba", "dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba")
        val months = listOf(
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"
        )
        return "Bugun ${c.get(Calendar.DAY_OF_MONTH)} ${months[c.get(Calendar.MONTH)]}, " +
            "${c.get(Calendar.YEAR)} yil, ${days[c.get(Calendar.DAY_OF_WEEK) - 1]}."
    }
}
