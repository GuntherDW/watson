package watson.db;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.logging.Level;

import watson.Configuration;
import watson.Controller;
import watson.DisplaySettings;
import watson.analysis.ServerTime;
import watson.chat.Chat;
import watson.chat.Colour;
import watson.debug.Log;

// ----------------------------------------------------------------------------
/**
 * A simple spatial database, mapping 3-D coordinates ({@link IntCoord}) to the
 * destruction of the original ore at that location and the corresponding
 * {@link OreDeposit} that was there.
 * 
 * This class currently only stores the coordinates of ores (block IDs 14, 15,
 * 16, 21, 56, 73, 74, 129 and 153). The complete editing history retrieved by
 * LogBlock is instead stored in {@link BlockEditSet}.
 * 
 * Note that an ore is considered an ore irrespective of whether ut normally
 * spawns in a given dimension with the vanilla Minecraft generator. So, for
 * example, quartz ore counts as an ore in the overworld. This allows the ore
 * indexing to work with custom terrain generators.
 */
public class OreDB
{
  /**
   * Constructor.
   */
  public OreDB()
  {
    BlockTypeRegistry types = BlockTypeRegistry.instance;

    // Add the TypedOreDB instances in the order that we would like to list
    // them to the user, i.e. ddiamonds, then emeralds, then iron...
    _db.put(types.getBlockTypeById(56), new TypedOreDB(200));
    _db.put(types.getBlockTypeById(129), new TypedOreDB(200));
    _db.put(types.getBlockTypeById(15), new TypedOreDB(400));
    _db.put(types.getBlockTypeById(14), new TypedOreDB(200));
    _db.put(types.getBlockTypeById(21), new TypedOreDB(200));
    // Merge redstone ore (73) and glowing redstone ore (74)
    _db.put(types.getBlockTypeById(74), new TypedOreDB(200));
    _db.put(types.getBlockTypeById(16), new TypedOreDB(800));
    _db.put(types.getBlockTypeById(153), new TypedOreDB(400));

    _chatColours.put(types.getBlockTypeById(56), Colour.lightblue);
    _chatColours.put(types.getBlockTypeById(129), Colour.lightgreen);
    _chatColours.put(types.getBlockTypeById(15), Colour.orange);
    _chatColours.put(types.getBlockTypeById(14), Colour.yellow);
    _chatColours.put(types.getBlockTypeById(21), Colour.blue);
    _chatColours.put(types.getBlockTypeById(73), Colour.red);
    _chatColours.put(types.getBlockTypeById(74), Colour.red);
    _chatColours.put(types.getBlockTypeById(16), Colour.grey);
    _chatColours.put(types.getBlockTypeById(153), Colour.white);
  } // OreDB

  // --------------------------------------------------------------------------
  /**
   * Clear all ore information from the database.
   */
  public void clear()
  {
    for (TypedOreDB db : _db.values())
    {
      db.clear();
    }

    // The first call to tpNex() will increment this to 1.
    _tpIndex = 0;
  } // clear

  // --------------------------------------------------------------------------
  /**
   * List all of the ore deposits in the database in chat.
   * 
   * @param page the 1-based page index to list; page numbers less than 1 are
   *          assumed to have been eliminated by the caller.
   */
  public void listDeposits(int page)
  {
    int depositCount = getOreDepositCount();
    int pages = (depositCount + Controller.PAGE_LINES - 1)
                / Controller.PAGE_LINES;

    if (depositCount == 0)
    {
      Chat.localOutput("There are no ore deposits.");
    }
    else
    {
      if (page > pages)
      {
        Chat.localError(String.format(Locale.US,
          "The highest page number is %d.", pages));
      }
      else
      {
        if (depositCount == 1)
        {
          Chat.localOutput("There is 1 ore deposit.");
        }
        else
        {
          Chat.localOutput(String.format(Locale.US,
            "There are %d ore deposits.", depositCount));
        }

        // Most of the time, there is only a single page of deposits and it is
        // more efficient therefore to iterate linearly to skip pages.
        int id = 1;
        int start = 1 + (page - 1) * Controller.PAGE_LINES;
        int end = start + Controller.PAGE_LINES;
        outer: for (TypedOreDB db : _db.values())
        {
          for (OreDeposit deposit : db.getOreDeposits())
          {
            if (id >= start && id < end)
            {
              long time = deposit.getTimeStamp();
              OreBlock block = deposit.getKeyOreBlock();
              BlockEdit edit = block.getEdit();
              BlockType type = edit.type;
              String player = edit.player;
              String strike = edit.playerEditSet.isVisible() ? "" : "\247m";
              String line = String.format(Locale.US,
                "\247%c%s(%3d) %s (% 5d % 3d % 5d) %2d [%2d] %s",
                _chatColours.get(type).getCode(), strike, id,
                TimeStamp.formatMonthDayTime(time), block.getLocation().getX(),
                block.getLocation().getY(), block.getLocation().getZ(),
                type.getId(), deposit.getBlockCount(), player);
              Chat.localChat(line);
            }

            ++id;
            if (id >= end)
            {
              break outer;
            }
          } // for all deposits of this ore type
        } // for all types of deposits

        if (page < pages)
        {
          Chat.localOutput(String.format(Locale.US,
            "Page %d of %d.", page, pages));
          Chat.localOutput(String.format(Locale.US,
            "Use \"/w ore %d\" to view the next page.", (page + 1)));
        }

      } // page number is valid
    } // there are ore deposits
  } // listDeposits

  // --------------------------------------------------------------------------
  /**
   * Return the number of {@link OreDeposit}s in the database.
   * 
   * @return the number of {@link OreDeposit}s in the database.
   */
  public int getOreDepositCount()
  {
    int count = 0;
    for (TypedOreDB db : _db.values())
    {
      count += db.getOreDepositCount();
    }
    return count;
  }

  // --------------------------------------------------------------------------
  /**
   * Return a reference to the {@link OreDeposit} with the specified 1-based
   * index.
   * 
   * An index less than one wraps around to the maximum, and an index greater
   * than the maximum wraps around to 1.
   * 
   * @param index the 1-based index of the {@link OreDeposit}.
   * @return the {@link OreDeposit} with the specified index.
   */
  public OreDeposit getOreDeposit(int index)
  {
    index = limitOreDepositIndex(index);
    for (TypedOreDB db : _db.values())
    {
      if (index <= db.getOreDepositCount())
      {
        return db.getOreDeposit(index);
      }
      else
      {
        index -= db.getOreDepositCount();
      }
    } // for
    throw new IllegalStateException("shouldn't happen");
  } // getOreDeposit

  // --------------------------------------------------------------------------
  /**
   * Remove all ore deposits mined by the specified player.
   * 
   * This method is called when "/w edits remove <player>" is executed.
   * 
   * @param player the case-insensitive player name.
   */
  public void removeDeposits(String player)
  {
    for (TypedOreDB db : _db.values())
    {
      db.removeDeposits(player);
    }
  }

  // --------------------------------------------------------------------------
  /**
   * Teleport to the next ore deposit.
   */
  public void tpNext()
  {
    tpIndex(_tpIndex + 1);
  }

  // --------------------------------------------------------------------------
  /**
   * Teleport to the previous ore deposit.
   */
  public void tpPrev()
  {
    tpIndex(_tpIndex - 1);
  }

  // --------------------------------------------------------------------------
  /**
   * Teleport to the ore deposit with the specified 1-based index.
   * 
   * @return the index teleported to.
   */
  public void tpIndex(int index)
  {
    if (getOreDepositCount() == 0)
    {
      Chat.localError("There are no ore deposits to teleport to.");
    }
    else
    {
      _tpIndex = index = limitOreDepositIndex(index);
      OreDeposit deposit = getOreDeposit(index);
      IntCoord coord = deposit.getKeyOreBlock().getLocation();
      Controller.instance.teleport(coord.getX(), coord.getY(), coord.getZ());
      Chat.localOutput(String.format(Locale.US,
        "Teleporting you to ore #%d", index));

      // "Select" the tp target so that /w pre will work.
      Controller.instance.selectBlockEdit(deposit.getKeyOreBlock().getEdit());

    }
  } // tpIndex

  // --------------------------------------------------------------------------
  /**
   * Show stone:diamond ratios for the overall mining session (all diamond ore
   * deposits) as well as time periods where diamonds are particularly close
   * together in time.
   */
  public void showRatios()
  {
    // If ServerTime doesn't have a stored time difference for this server,
    // do a query to find that out.
    ServerTime.instance.queryServerTime(false);
    TypedOreDB diamonds = getDB(BlockTypeRegistry.instance.getBlockTypeById(56));

    // Show the overall ratio for all mining.
    if (diamonds.getOreDepositCount() != 0)
    {
      showRatio(diamonds.getOreDeposits().first(),
        diamonds.getOreDeposits().last());
      // Find time periods where there are 3 more more deposits in a 15 minute
      // period.
      int count = 0;
      OreDeposit first = null;
      OreDeposit last = null;
      long lastTime = 0;
      for (OreDeposit deposit : diamonds.getOreDeposits())
      {
        long depositTime = deposit.getKeyOreBlock().getEdit().time;

        // Visiting first deposit ever?
        if (first == null)
        {
          first = last = deposit;
          count = 1;
        }
        else
        {
          // If the next deposit is more than 7 minutes after the previous, or
          // if
          // this is the last of the ore deposits, then our run of consecutive
          // deposits is over.
          if (Math.abs(depositTime - lastTime) > 7 * 60 * 1000
              || deposit == diamonds.getOreDeposits().last())
          {
            if (deposit == diamonds.getOreDeposits().last())
            {
              last = deposit;
            }

            // Check whether we have 3 deposits in a row and should list ratios.
            // No point in calculating the ratio for ALL deposits here, since we
            // do that anyway.
            if (count >= 3
                && (first != diamonds.getOreDeposits().first() || last != diamonds.getOreDeposits().last()))
            {
              showRatio(first, last);
            }

            first = last = deposit;
            count = 1;
          }
          else
          {
            ++count;
            last = deposit;
          }
        }
        lastTime = depositTime;
      } // for
    } // if there are deposits
    else
    {
      Chat.localOutput("There are no diamond ore deposits.");
    }
  } // showRatios

  // --------------------------------------------------------------------------
  /**
   * Automatically run "/w pre" to show the tunnels leading up to a configured
   * maximum number of diamond deposits.
   */
  public void showTunnels()
  {
    // TODO: This won't work without a way of queueing up commands over a long
    // period of time and waiting for each to complete.
    // // Show at most the configured maximum number of tunnels.
    // TypedOreDB diamonds =
    // getDB(BlockTypeRegistry.instance.getBlockTypeById(56));
    // int count = 0;
    // for (OreDeposit deposit : diamonds.getOreDeposits())
    // {
    // Controller.instance.selectBlockEdit(deposit.getKeyOreBlock().getEdit());
    // Controller.instance.queryPreviousEdits();
    //
    // ++count;
    // // TODO: Replace magic number with configuration setting.
    // if (count > 10)
    // {
    // break;
    // }
    // } // for
  } // showTunnels

  // --------------------------------------------------------------------------
  /**
   * Examine the edit to see if it is an ore, and if it is, add it to the
   * database.
   * 
   * @param edit the edit to examine.
   */
  public void addBlockEdit(BlockEdit edit)
  {
    try
    {
      // Only interested in edits that correspond to destruction of an ore.
      // Merge glowing and no-glowing redstone into the same TypedOreDB.
      BlockType mergedType = OreDB.getMergedBlockType(edit.type);
      if (!edit.creation && isOre(mergedType))
      {
        TypedOreDB db = getDB(mergedType);
        db.addBlockEdit(edit);
      }
    }
    catch (Exception ex)
    {
      Log.exception(Level.SEVERE, "error in OreDB.addBlockEdit()", ex);
    }
  } // addBlockEdit

  // --------------------------------------------------------------------------
  /**
   * Draw a label (billboard) for each ore deposit.
   */
  public void drawDepositLabels()
  {
    DisplaySettings settings = Controller.instance.getDisplaySettings();
    if (settings.areLabelsShown())
    {
      int id = 1;
      StringBuilder label = new StringBuilder();
      for (TypedOreDB db : _db.values())
      {
        for (OreDeposit deposit : db.getOreDeposits())
        {
          OreBlock block = deposit.getKeyOreBlock();
          if (block.getEdit().playerEditSet.isVisible())
          {
            label.setLength(0);
            label.ensureCapacity(4);
            label.append(id);
            Annotation.drawBillboard(
              block.getLocation().getX(),
              block.getLocation().getY(),
              block.getLocation().getZ(),
              Configuration.instance.getBillboardBackground(),
              Configuration.instance.getBillboardForeground(),
              0.03,
              label.toString());
          }
          ++id;
        } // for all deposits
      } // for all ore types
    } // if drawing deposit labels
  } // drawDepositLabels

  // --------------------------------------------------------------------------
  /**
   * Issue a LogBlock query for the time period 7 minutes before the specified
   * first {@link OreDeposit} to the start of the next minute after the last
   * {@link OreDeposit}.
   * 
   * @param first the first deposit.
   * @param last the last deposit.
   */
  protected void showRatio(OreDeposit first, OreDeposit last)
  {
    // Time stamps of the start and end of the mining period.
    Calendar startTime = Calendar.getInstance();
    Calendar endTime = Calendar.getInstance();
    startTime.setTimeInMillis(first.getEarliestEdit().time);
    endTime.setTimeInMillis(last.getLatestEdit().time);

    // 7 minutes before the first diamond: 7 * 60s * 1.3 block/s = ~550 blocks
    // Round the seconds value down to 0.
    startTime.add(Calendar.MINUTE, -7);
    startTime.set(Calendar.SECOND, 0);

    // Next minute after the last diamond edit.
    endTime.add(Calendar.MINUTE, 1);
    endTime.set(Calendar.SECOND, 0);

    // Query:
    // /lb player name since <firstTime> before <lastTime>
    // block <block IDs> sum blocks
    // Include ores, stone, dirt and gravel in the block IDs.
    //
    // Assume that the player is the same for all diamonds in the period.
    String player = first.getKeyOreBlock().getEdit().player;
    String sinceTime = TimeStamp.formatQueryTime(startTime.getTimeInMillis());
    String beforeTime = TimeStamp.formatQueryTime(endTime.getTimeInMillis());

    // The player name is at most 16 characters. As such, this query will be 96
    // characters long and will NOT result in a kick for being >100 characters.
    String query = String.format(Locale.US,
      "/lb player %s since %s before %s sum b block 1 56", player, sinceTime,
      beforeTime);
    Log.debug(query);
    Controller.instance.serverChat(query);
  } // showRatio

  // --------------------------------------------------------------------------
  /**
   * Return the {@link TypedOreDB} instance applicable to the specified ore
   * type.
   * 
   * @param type the {@link BlockType} of the ore.
   * @return the {@link TypedOreDB} that stores information about
   *         {@link OreDeposits} of that type.
   */
  protected TypedOreDB getDB(BlockType type)
  {
    return _db.get(type);
  }

  // --------------------------------------------------------------------------
  /**
   * Return true if the specified {@link BlockType} is an ore.
   * 
   * @param type the block type.
   * @return true if the specified {@link BlockType} is an ore.
   */
  protected boolean isOre(BlockType type)
  {
    return _db.containsKey(type);
  }

  // --------------------------------------------------------------------------
  /**
   * Limit the specified {@link OreDeposit} index to the valid range.
   * 
   * @param index the 1-based index.
   * @return an index in the valid range [1,getOreDepositCount()].
   */
  protected int limitOreDepositIndex(int index)
  {
    if (index < 1)
    {
      return getOreDepositCount();
    }
    else if (index > getOreDepositCount())
    {
      return 1;
    }
    else
    {
      return index;
    }
  } // limitOreDepositIndex

  // --------------------------------------------------------------------------
  /**
   * If the specified BlockType is redstone ore, return (merge it with) glowing
   * redstone ore.
   * 
   * @param type the type of ore.
   * @return redstone ore for both redstone ore types, or the original type
   *         parameter if not redstone.
   */
  static protected BlockType getMergedBlockType(BlockType type)
  {
    if (type.getId() == 73)
    {
      return BlockTypeRegistry.instance.getBlockTypeById(74);
    }
    else
    {
      return type;
    }
  } // getMergedBlockType

  // --------------------------------------------------------------------------
  /**
   * Map from {@link BlockType} to {@link TypedOreDB}, linked in the order that
   * we would like to list ore deposits to the user, i.e. diamonds first.
   */
  protected LinkedHashMap<BlockType, TypedOreDB> _db          = new LinkedHashMap<BlockType, TypedOreDB>();

  /**
   * A map from the {@link BlockType} to the {@link Colour} to use when listing
   * ores of that type in chat.
   */
  protected LinkedHashMap<BlockType, Colour>     _chatColours = new LinkedHashMap<BlockType, Colour>();

  /**
   * The index of the most recently teleported to {@link OreDeposit}.
   */
  protected int                                  _tpIndex     = 0;

} // class OreDB
