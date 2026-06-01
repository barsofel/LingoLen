package ofelya.barseghyan.lingolens

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    private val libraryFragment = LibraryFragment()
    private val dictionaryFragment = DictionaryFragment()
    private val repeatFragment = RepeatFragment()

    private var activeFragment: Fragment = libraryFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)

        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragmentContainer, repeatFragment, "repeat").hide(repeatFragment)
            add(R.id.fragmentContainer, dictionaryFragment, "dictionary").hide(dictionaryFragment)
            add(R.id.fragmentContainer, libraryFragment, "library")
        }.commit()

        bottomNav.setOnItemSelectedListener { item ->
            val target: Fragment = when (item.itemId) {
                R.id.nav_library -> libraryFragment
                R.id.nav_dictionary -> dictionaryFragment
                R.id.nav_repeat -> repeatFragment
                else -> return@setOnItemSelectedListener false
            }
            switchFragment(target)
            true
        }
    }

    private fun switchFragment(target: Fragment) {
        if (target == activeFragment) return
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(target)
            .commit()
        activeFragment = target
    }
    fun switchToTab(tabId: Int) {
        bottomNav.selectedItemId = tabId
    }
}