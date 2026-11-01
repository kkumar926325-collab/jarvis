package com.jarvis.assistant

import com.jarvis.assistant.core.Command
import com.jarvis.assistant.core.CommandParser
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {
    private fun p(s: String) = CommandParser.parse(s)

    @Test fun call() {
        assertEquals(Command.Call("mom"), p("call mom"))
        assertEquals(Command.Call("Rahul Kumar"), p("Hey Jarvis, call Rahul Kumar please"))
        assertEquals(Command.Call("9876543210"), p("dial 9876543210"))
    }

    @Test fun sendSms() {
        assertEquals(Command.SendSms("Rahul", "I'm running late!"), p("send sms to Rahul saying I'm running late!"))
        assertEquals(Command.SendSms("mom", "be home soon"), p("text mom: be home soon"))
        assertEquals(Command.SendSms("Priya", "ok"), p("message Priya saying ok"))
    }

    @Test fun readSms() {
        assertEquals(Command.ReadSms(5), p("read my messages"))
        assertEquals(Command.ReadSms(3), p("read my last 3 messages"))
    }

    @Test fun appsAndSettings() {
        assertEquals(Command.OpenApp("WhatsApp"), p("open WhatsApp"))
        assertEquals(Command.AppInfo("whatsapp"), p("manage whatsapp"))
        assertEquals(Command.ListApps, p("list apps"))
        assertEquals(Command.OpenSettings("wifi", false), p("open wifi settings"))
        assertEquals(Command.OpenSettings("bluetooth", true), p("turn off bluetooth"))
        assertEquals(Command.OpenSettings("battery saver"), p("battery saver"))
        assertEquals(Command.OpenSettings("settings"), p("open settings"))
    }

    @Test fun deviceInfo() {
        assertEquals(Command.Battery, p("how much battery is left"))
        assertEquals(Command.DeviceInfo, p("device info"))
        assertEquals(Command.DeviceInfo, p("phone info"))
        assertEquals(Command.Call("mom"), p("call mom"))
        assertEquals(Command.ReadNotifications, p("what are my notifications"))
    }

    @Test fun files() {
        assertEquals(Command.ListFiles(null), p("list files"))
        assertEquals(Command.ListFiles("Download/Reports"), p("list files in Download/Reports"))
        assertEquals(Command.SearchFiles("budget"), p("find file budget"))
        assertEquals(Command.PickFolder, p("choose folder"))
    }

    @Test fun contacts() {
        assertEquals(Command.FindContact("priya"), p("find contact priya"))
    }

    @Test fun memory() {
        assertEquals(Command.Remember("mom", "Sunita Devi"), p("remember mom is Sunita Devi"))
        assertEquals(Command.Teach("good morning", "battery status"), p("when I say good morning do battery status"))
        assertEquals(Command.ClearMemory, p("forget everything"))
        assertEquals(Command.Forget("mom"), p("forget mom"))
        assertEquals(Command.ListMemory, p("what do you remember"))
        assertEquals(Command.SetSmsMode(false), p("set sms mode to compose"))
    }

    @Test fun helpAndUnknown() {
        assertEquals(Command.Help, p("help"))
        assertEquals(Command.Unknown("what's the weather"), p("what's the weather"))
        assertEquals(Command.Unknown(""), p("   "))
    }
}
