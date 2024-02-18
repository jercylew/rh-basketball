package com.ruihao.basketball

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen


class SettingsFragment : PreferenceFragmentCompat(),
    OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.setting_pref)
        val sharedPreferences = preferenceScreen.sharedPreferences
        val prefScreen: PreferenceScreen = preferenceScreen
        val count: Int = prefScreen.preferenceCount

        // Go through all of the preferences, and set up their preference summary.
        for (i in 0 until count) {
            val p: Preference = prefScreen.getPreference(i)
            // You don't need to set up preference summaries for checkbox preferences because
            // they are already set up in xml using summaryOff and summary On
            if (p !is CheckBoxPreference) {
                val value = sharedPreferences!!.getString(p.getKey(), "")
                setPreferenceSummary(p, value)
            }
        }
        val preference: Preference? = findPreference(getString(R.string.pref_size_key))
        preference?.onPreferenceChangeListener = this
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val error = Toast.makeText(context, "Please select a number.", Toast.LENGTH_SHORT)
        val sizeKey = getString(R.string.pref_size_key)
        if (preference.key.equals(sizeKey)) {
            val stringSize = newValue as String
            try {
                val size = stringSize.toFloat()
            } catch (nfe: NumberFormatException) {
                error.show()
                return false
            }
        }
        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        // Figure out which preference was changed
        val preference: Preference? = findPreference(key)
        if (null != preference) {
            // Updates the summary for the preference
            if (preference !is CheckBoxPreference) {
                val value = sharedPreferences.getString(preference.getKey(), "")
                setPreferenceSummary(preference, value)
            }
        }
    }

    private fun setPreferenceSummary(preference: Preference, value: String?) {
        if (preference is ListPreference) {
            // For list preferences, figure out the label of the selected value
            val listPreference: ListPreference = preference
            val prefIndex: Int = listPreference.findIndexOfValue(value)
            if (prefIndex >= 0) {
                // Set the summary to that label
                listPreference.summary = listPreference.entries[prefIndex]
            }
        } else if (preference is EditTextPreference) {
            // For EditTextPreferences, set the summary to the value's simple string representation.
            preference.setSummary(value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceScreen.sharedPreferences
            ?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceScreen.sharedPreferences
            ?.unregisterOnSharedPreferenceChangeListener(this)
    }
}