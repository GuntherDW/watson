package watson.chat;

import java.util.logging.Level;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import watson.debug.Log;

// ----------------------------------------------------------------------------
/**
 * Handles local and to-server chat, including highlighting of received chat
 * messages.
 */
public class Chat
{
  // --------------------------------------------------------------------------
  /**
   * Return the (@link ChatHighlighter} that colours naughty words and whatnot
   * in chat lines.
   * 
   * @return the {@link ChatHighlighter}.
   */
  public static ChatHighlighter getChatHighlighter()
  {
    return _chatHighlighter;
  }

  // --------------------------------------------------------------------------
  /**
   * Send a chat message to the server.
   * 
   * @param message the text to display.
   */
  public static void serverChat(String message)
  {
    try
    {
      Minecraft mc = Minecraft.getMinecraft();
      mc.thePlayer.sendChatMessage(message);
    }
    catch (Exception ex)
    {
      Log.exception(Level.SEVERE, "Sending chat to the server.", ex);
    }
  }

  // --------------------------------------------------------------------------
  /**
   * Return true if the chat GUI is ready to display chat.
   * 
   * @return true if the chat GUI is ready to display chat.
   */
  public static boolean isChatGuiReady()
  {
    Minecraft mc = Minecraft.getMinecraft();
    return mc.ingameGUI != null && mc.ingameGUI.getChatGUI() != null;
  }

  // --------------------------------------------------------------------------
  /**
   * Display a chat message locally.
   * 
   * @param message the text to display.
   */
  public static void localChat(String message)
  {
    localChat(new ChatComponentText(message));
  }

  // --------------------------------------------------------------------------
  /**
   * Display a chat message locally.
   * 
   * @param colour the colour to format the text as.
   * @param message the text to display.
   */
  public static void localChat(EnumChatFormatting colour, String message)
  {
    ChatComponentText chat = new ChatComponentText(message);
    ChatStyle style = new ChatStyle();
    style.setColor(colour);
    chat.setChatStyle(style);
    localChat(chat);
  }

  // --------------------------------------------------------------------------
  /**
   * Display the chat locally.
   * 
   * @param chat the chat component.
   */
  public static void localChat(IChatComponent chat)
  {
    if (isChatGuiReady())
    {
      IChatComponent highlighted = getChatHighlighter().highlight(chat);
      Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(highlighted);
    }
  }

  // --------------------------------------------------------------------------
  /**
   * Display the output of Watson commands, locally in the client.
   * 
   * @param message the text to display.
   */
  public static void localOutput(String message)
  {
    localChat(EnumChatFormatting.AQUA, message);
  }

  // --------------------------------------------------------------------------
  /**
   * Display Watson error messages, locally in the client.
   * 
   * @param message the text to display.
   */
  public static void localError(String message)
  {
    localChat(EnumChatFormatting.DARK_RED, message);
  }

  // --------------------------------------------------------------------------
  /**
   * The chat highlighter.
   */
  protected static ChatHighlighter _chatHighlighter = new ChatHighlighter();
} // class Chat