package com.github.hhhzzzsss.songplayer;

import com.github.hhhzzzsss.songplayer.config.ModProperties;
import com.github.hhhzzzsss.songplayer.playing.SongHandler;
import com.github.hhhzzzsss.songplayer.song.Note;
import com.github.hhhzzzsss.songplayer.song.Song;
import com.google.common.io.Files;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CommandProcessor {
	public static ArrayList<Command> commands = new ArrayList<>();
	public static HashMap<String, Command> commandMap = new HashMap<>();
	public static ArrayList<String> commandCompletions = new ArrayList<>();
	private static ArrayList<String> possibleArguments = new ArrayList<>();

	public static void initCommands() {
		commands.add(new helpCommand());
		commands.add(new playCommand());
		commands.add(new stopCommand());
		commands.add(new skipCommand());
		commands.add(new gotoCommand());
		commands.add(new loopCommand());
		commands.add(new playlistCommand());
		commands.add(new statusCommand());
		commands.add(new queueCommand());
		commands.add(new songsCommand());
		commands.add(new playmodeCommand());
		commands.add(new setStage());
		commands.add(new setCommandCommand());
		commands.add(new setCreativeCommandCommand());
		commands.add(new setSurvivalCommandCommand());
		commands.add(new useEssentialsCommandsCommand());
		commands.add(new useVanillaCommandsCommand());
		commands.add(new toggleFakePlayerCommand());
		commands.add(new testSongCommand());
		commands.add(new toggleCommand());
		commands.add(new prefixCommand());

		for (Command command : commands) {
			commandMap.put(command.getName().toLowerCase(), command);
			commandCompletions.add(command.getName());
			for (String alias : command.getAliases()) {
				commandMap.put(alias.toLowerCase(), command);
				commandCompletions.add(alias);
			}
		}
	}

	// returns true if it is a command and should be cancelled
	public static boolean processChatMessage(String message) {
		if (!message.startsWith(String.valueOf(SongPlayer.prefix))) {
			return false;
		}
		String[] parts = message.substring(SongPlayer.prefix.length()).split(" ");
		String name = parts.length>0 ? parts[0] : "";
		String args = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
		Command c = commandMap.get(name.toLowerCase());
		if (c == null) {
			SongPlayer.addChatMessage("§cUnrecognized command");
		} else {
			boolean success = c.processCommand(args);
			if (!success) {
				SongPlayer.addChatMessage("§6Syntax - §c" + c.getSyntax());
			}
		}
		return true;
	}

	private static abstract class Command {
    	public abstract String getName();
    	public abstract String getSyntax();
    	public abstract String getDescription();
    	public abstract boolean processCommand(String args);
		public String[] getAliases() {
			return new String[]{};
		}
		public CompletableFuture<Suggestions> getSuggestions(String args, SuggestionsBuilder suggestionsBuilder) {
			return null;
		}
    }

	private static class setCommandCommand extends Command {
		@Override
		public String getName() {
			return "setCommand";
		}
		@Override
		public String[] getAliases() { return new String[] {"setCmd"}; }

		@Override
		public String getSyntax() {
			return SongPlayer.prefix + "setCommand <survival, creative, playnote, displayprogress> <command>";
		}

		@Override
		public String getDescription() {
			return "Changes what commands to run while playing. Use " + SongPlayer.prefix + "setCommand <command name> to get more information about when that command will be ran.\n[There are variables like: {type} = instrument, {volume} = 0.0 - 1.0, {pitch} = pitch of note, {CurrentTime} | {SongTime} = time the song has progressed | duration of song, {MIDI} = current song playing]";
		}

		@Override
		public boolean processCommand(String args) {
			String[] theArgs = args.split(" ");
			if (theArgs.length == 0) {
				return false;
			}
			String[] Commands = {"survival", "creative", "playnote", "displayprogress"};
			String[] configName = {"survivalCommand", "creativeCommand", "playSoundCommand", "showProgressCommand"};
			String[] Values = {SongPlayer.survivalCommand, SongPlayer.creativeCommand, SongPlayer.playSoundCommand, SongPlayer.showProgressCommand};
			for (int i = 0; i < Commands.length; i++) {
				if (theArgs[0].equalsIgnoreCase(Commands[i])) {
					if (theArgs.length == 1) {
						SongPlayer.addChatMessage("§6The command §3" + Commands[i] + "§6 is currently set to: §3/" + Values[i]);
						return true;
					}
					StringBuilder ncmd = new StringBuilder();
					String newCommand = "";
					for (int s = 1; s < theArgs.length; s++) {
						ncmd.append(theArgs[s] + " ");
					}
					newCommand = ncmd.toString().trim();
					if (newCommand.startsWith("/")) {
						newCommand = newCommand.substring(1, newCommand.length());
					}
					if (Values[i].equals(newCommand)) {
						SongPlayer.addChatMessage("§6Nothing changed; command is already set to §3" + Values[i]);
						return true;
					}
					SongPlayer.addChatMessage("§6The command §3" + Commands[i] + "§6 has been updated\nfrom: §3/" + Values[i] + "\n§6to: §3/" + newCommand);
					ModProperties.getInstance().updateValue(configName[i], newCommand);
					Util.updateValuesToConfig();
					return true;
				}
			}
			return false;
		}

		public CompletableFuture<Suggestions> getSuggestions(String args, SuggestionsBuilder suggestionsBuilder) {
			String[] theArgs = args.split(" ");
			int argumentint = theArgs.length;

			if (args.endsWith(" ")) argumentint += 1;
			if (argumentint < 2) {
				possibleArguments.addAll(List.of(new String[]{"survival", "creative", "playnote", "displayprogress"}));
			} else {
				possibleArguments.add("<command>");
			}
			return CommandSource.suggestMatching(possibleArguments, suggestionsBuilder);
		}
	}

	private static class playlistCommand extends Command {
		@Override
		public String getName() {
			return "playlist";
		}
		@Override
		public String[] getAliases() {
			return new String[]{"plist"};
		}
		@Override
		public String getSyntax() {
			return SongPlayer.prefix + "playlist edit <playlist> <add, remove> <song>\n§6OR: §c" + SongPlayer.prefix + "playlist <create, delete, play> <playlist>\n§6OR: §c" + SongPlayer.prefix + "playlist sort <addedfirst, alphabetically, shuffle>";
		}

		@Override
		public String getDescription() {
			return "automatically load a set list of songs or manage your playlists";
		}

		@Override
		public boolean processCommand(String args) {
			String[] theArgs = args.split(" ");
			if (theArgs.length < 2) {
				return false;
			}
			File playlist = new File("SongPlayer/playlists/" + theArgs[1]);;
			File songsfolder = SongPlayer.SONG_DIR;
			switch(theArgs[0].toLowerCase()) {
				case "create": {
					if (playlist.exists()) {
						SongPlayer.addChatMessage("§6playlist §3" + theArgs[1] + "§6 already exists!");
						return true;
					}
					playlist.mkdir();
					SongPlayer.addChatMessage("§6Added playlist named §3" + theArgs[1]);
					return true;
				}
				case "delete": {
					if (!playlist.exists()) {
						SongPlayer.addChatMessage("§6playlist §3" + theArgs[1] + "§6 doesn't exist!");
						return true;
					}
					if (Util.currentPlaylist.equals(theArgs[1])) {
						SongPlayer.addChatMessage("§6you cannot modify playlists that are playing.");
						return true;
					}
					try {
						FileUtils.deleteDirectory(playlist);
						SongPlayer.addChatMessage("§6deleted playlist §3" + theArgs[1]);
					} catch(IOException e) {
						SongPlayer.addChatMessage("§cThere was an internal error attempting to delete this playlist. Check your logs for more details.");
						e.printStackTrace();
						System.out.println("Crud... This isn't what my mod is supposed to do!");
					}
					return true;
				}
				case "edit": {
					//general
					if (theArgs.length < 4) {
						return false;
					}
					if (!playlist.exists()) {
						SongPlayer.addChatMessage("§6playlist §3" + theArgs[1] + "§6 doesn't exist!");
						return true;
					}
					if (Util.currentPlaylist.equals(theArgs[1])) {
						SongPlayer.addChatMessage("§6you cannot modify playlists that are playing.");
						return true;
					}
					StringBuilder miditogetbuilder = new StringBuilder();
					for (int i = 3; i < theArgs.length; i++) {
						miditogetbuilder.append(theArgs[i] + " ");
					}
					String miditoget = miditogetbuilder.toString().substring(0, miditogetbuilder.length() - 1);
					if (theArgs[2].equalsIgnoreCase("add")) {
						File toget = new File("songs/" + miditoget);
						for (String file : playlist.list()) {
							if (file.equals(miditoget)) {
								SongPlayer.addChatMessage("§3" + miditoget + "§6 already exists in this playlist!");
								return true;
							}
						}
						for (String file : songsfolder.list()) {
							if (file.equals(miditoget)) {
								try {
									Files.copy(toget, new File("SongPlayer/playlists/" + theArgs[1] + "/" + miditoget));
									SongPlayer.addChatMessage("§6added §3" + miditoget + "§6 to the playlist §9" + theArgs[1]);
								} catch(IOException e) {
									e.printStackTrace();
									SongPlayer.addChatMessage("§cThere was an error copying one or more files.");
								}
								return true;
							}
						}
					} else if (theArgs[2].equalsIgnoreCase("remove")) {
						File toget = new File("SongPlayer/playlists/" + theArgs[1] + "/" + miditoget);
						for (String file : songsfolder.list()) {
							if (file.equals(miditoget)) {
								toget.delete();
								SongPlayer.addChatMessage("§6removed §3" + miditoget + "§6 from the playlist §3" + theArgs[1]);
								return true;
							}
						}
						SongPlayer.addChatMessage("§cthat file doesn't exist");
						return true;
					} else {
						return false;
					}
					SongPlayer.addChatMessage("§cCould not find file.");
					return true;
				}
				case "play": {
					if (!Util.currentPlaylist.isEmpty()) {
						SongPlayer.addChatMessage("§6a playlist is already running!");
						return true;
					}
					if (!playlist.exists()) {
						SongPlayer.addChatMessage("§6playlist §3" + theArgs[1] + "§6 doesn't exist!");
						return true;
					}
					File[] songs = playlist.listFiles();
					String[] songnames = playlist.list();
					if (songs.length == 0) {
						SongPlayer.addChatMessage("§6no songs in playlist!");
						return true;
					}
					if (SongHandler.getInstance().stage != null) {
						SongHandler.getInstance().stage.movePlayerToStagePosition();
					}
					Util.playlistSongs.clear();
					SongHandler.getInstance().cleanup(true);
					//Util.loadSongs(songs, 0, playlist);
					switch(Util.playlistOrder) {
						case "addedfirst": {
							break;
						}
						case "alphabetically": {
							Arrays.sort(songnames, java.text.Collator.getInstance());
							break;
						}
						case "shuffle": {
							List<String> shuffle = Arrays.asList(songnames);
							Collections.shuffle(shuffle);
							songnames = shuffle.toArray(new String[shuffle.size()]);
							break;
						}
						default: {
							SongPlayer.addChatMessage("§cUnable to parse sorting method §4" + Util.playlistOrder + "§c. Using defaults. (addedfirst)");
						}
					}

					Util.playlistSongs.addAll(Arrays.asList(songnames));

					//SongHandler.getInstance().loadSong(Util.playlistSongs.get(0).getName(), playlist);
					if (Util.playlistSongs.size() < songs.length) {
						SongPlayer.addChatMessage("§cFailed to load some songs from playlist.");
					}
					Util.currentPlaylist = theArgs[1];
					SongPlayer.addChatMessage("§6loaded §9" + Util.playlistSongs.size() + "§6 songs from the playlist §3" + theArgs[1]);
					SongHandler.getInstance().loadSong(Util.playlistSongs.get(0), playlist);
					return true;
				}
				case "sort": {
					if (theArgs.length != 2) {
						return false;
					}
					switch(theArgs[1].toLowerCase()) {
						case "addedfirst":
						case "alphabetically":
						case "shuffle": {
							Util.playlistOrder = theArgs[1].toLowerCase();
							SongPlayer.addChatMessage("§6Playlist order now set to §3" + Util.playlistOrder);
							if (!Util.currentPlaylist.isEmpty()) {
								SongPlayer.addChatMessage("§9Changes will apply on the next playlist");
							}
							return true;
						}
						default: {
							SongPlayer.addChatMessage("§cUnknown order §4" + theArgs[1] + "§c.");
							return false;
						}
					}
				}
			}
			return false;
		}

		public CompletableFuture<Suggestions> getSuggestions(String args, SuggestionsBuilder suggestionsBuilder) {
			String[] theArgs = args.split(" ");
			int argumentint = theArgs.length;

			if (args.endsWith(" ")) argumentint += 1;

			switch(argumentint) {
				case 0:
				case 1: {
					possibleArguments.add("create");
					possibleArguments.add("delete");
					possibleArguments.add("edit");
					possibleArguments.add("play");
					possibleArguments.add("sort");
					break;
				}

				//list playlists here
				case 2: {
					if (theArgs[0].equalsIgnoreCase("create")) {
						possibleArguments.add("<playlist name>");
						break;
					} else if (theArgs[0].equalsIgnoreCase("sort")) {
						possibleArguments.add("addedfirst");
						possibleArguments.add("alphabetically");
						possibleArguments.add("shuffle");
						break;
					}
					try {
						List<String> filenames = Arrays.stream(Objects.requireNonNull(SongPlayer.PLAYLISTS_DIR.list())).toList();
						return CommandSource.suggestMatching(filenames, suggestionsBuilder);
					} catch(NullPointerException e) {
						if (!SongPlayer.SONG_DIR.exists()) {
							SongPlayer.SONG_DIR.mkdir();
						}
						return null;
					}
				}
				//if the beginning argument is "edit" do a different branch. else don't return anything
				case 3: {
					if (!theArgs[0].equalsIgnoreCase("edit")) {
						return null;
					}
					possibleArguments.add("add");
					possibleArguments.add("remove");
					break;
				}
				//if the beginning argument is "edit" and if 3rd argument is valid, list all the songs here. Otherwise return null.
				//sidenote: this is default because songs may have spaces in them.
				default: {
					if (!theArgs[0].equalsIgnoreCase("edit")) {
						return null;
					}
					boolean hasFile = false;
					for (File file : SongPlayer.PLAYLISTS_DIR.listFiles()) {
						if (file.getName().equals(theArgs[1])) {
							hasFile = true;
							break;
						}
					}
					if (!hasFile) {
						return null;
					}
					File playlistdir = new File("SongPlayer/playlists/" + theArgs[1] + "/");
					List<String> filenames = new ArrayList<>();
					if (theArgs[2].equalsIgnoreCase("add")) {
						filenames = Arrays.stream(SongPlayer.SONG_DIR.listFiles())
								.filter(File::isFile)
								.map(File::getName)
								.collect(Collectors.toList());
						for (String filenamesl : playlistdir.list()) {
							if (filenames.contains(filenamesl)) {
								filenames.remove(filenamesl);
							}
						}
					} else if (theArgs[2].equalsIgnoreCase("remove")) {
						filenames = Arrays.stream(playlistdir.listFiles())
								.filter(File::isFile)
								.map(File::getName)
								.collect(Collectors.toList());
					} else {
						return null;
					}
					return CommandSource.suggestMatching(filenames, suggestionsBuilder);
				}
			}

			return CommandSource.suggestMatching(possibleArguments, suggestionsBuilder);
		}
	}

	private static class setStage extends Command {

		@Override
		public String getName() {
			return "setStage";
		}

		@Override
		public String[] getAliases() {
			return new String[]{"stage", "updateStage"};
		}

		@Override
		public String getSyntax() {
			return SongPlayer.prefix + "setStage <default, legacy, compact>";
		}

		@Override
		public String getDescription() {
			return "Changes how the stage will be built when using gamemode method for playing songs. default places 353 noteblocks, legacy places 300, and compact places 400.\n Huge thanks to Lizard16 for the compact stage design!";
		}

		@Override
		public boolean processCommand(String args) {
			String[] arguments = args.toLowerCase().split(" ");
			if (arguments.length != 1) {
				return false;
			}
			switch(arguments[0].toLowerCase()) {
				case "default": {
					ModProperties.getInstance().updateValue("stageType", "default");
					Util.updateValuesToConfig();
					break;
				}
				case "legacy": {
					ModProperties.getInstance().updateValue("stageType", "legacy");
					Util.updateValuesToConfig();
					break;
				}
				case "compact": {
					ModProperties.getInstance().updateValue("stageType", "compact");
					Util.updateValuesToConfig();
					break;
				}
				default: {
					return false;
				}
			}
			SongPlayer.addChatMessage("§6Stage type is set to §3" + arguments[0].toLowerCase());
			return true;
		}

		public CompletableFuture<Suggestions> getSuggestions(String args, SuggestionsBuilder suggestionsBuilder) {
			String[] theArgs = args.split(" ");
			int argumentint = theArgs.length;

			if (args.endsWith(" ")) {
				argumentint += 1;
			}
			switch(argumentint) {
				case 0:
				case 1: {
					possibleArguments.add("default");
					possibleArguments.add("legacy");
					possibleArguments.add("compact");
					break;
				}
				default: {
					return null;
				}
			}
			return CommandSource.suggestMatching(possibleArguments, suggestionsBuilder);
		}
	}

	private static class playmodeCommand extends Command {

		@Override
		public String getName() {
			return "setPlayMode";
		}

		@Override
		public String[] getAliases() {
			return new String[]{"pMode", "playMode", "updatePlayMode"};
		}

		@Override
		public String getSyntax() {
			return SongPlayer.prefix + "setPlayMode [client, commands, gamemode]";
		}

		@Override
		public String getDescription() {
			return "Change what method to use when playing songs.\n " +
					"gamemode - will switch from creative to build noteblocks, and switch to survival to play them.\n " +
					"commands - will use only commands and no noteblocks. This should only be used if you have operator, as you can get kicked for spam otherwise.\n " +
					"client - plays noteblocks client-side. Can be used to test out new songs you get before playing them for everyone else.";
		}

		@Override
		public boolean processCommand(String args) {
			String[] arguments = args.toLowerCase().split(" ");
			if (arguments.length != 1) {
				return false;
			}
			//boolean usingcommands = SongPlayer.useCommandsForPlaying;
			//boolean switchinggamemode = SongPlayer.switchGamemode;
			switch (arguments[0].toLowerCase()) {
				case "commands": {
					if (SongPlayer.switchGamemode) {
						if (SongHandler.getInstance().stage != null) {
							SongHandler.getInstance().stage.movePlayerToStagePosition(true, false, false);
						}
					}
					if (SongPlayer.showFakePlayer && SongPlayer.fakePlayer != null) { //fake player is showing
						SongPlayer.removeFakePlayer();
					}
					SongPlayer.addChatMessage("§6Changed method to using commands");
					if (SongPlayer.useCommandsForPlaying) { //prevent my crappy spaghetti code from breaking - dont run code below if this is already true
						return true;
					}
					ModProperties.getInstance().updateValue("useCommandsForPlaying", String.valueOf(true));
					ModProperties.getInstance().updateValue("switchGamemode", String.valueOf(false));
					Util.updateValuesToConfig();
					Util.disableFlightIfNeeded();
					if (SongHandler.getInstance().currentSong != null) {
						Util.enableFlightIfNeeded();
					}
					return true;
				}
				case "survival": {
					return true;/*
					ModProperties.getInstance().updateValue("useCommandsForPlaying", String.valueOf(false));
					ModProperties.getInstance().updateValue("switchGamemode", String.valueOf(false));
					Util.updateValuesToConfig();
					if (SongHandler.getInstance().stage != null) {
						Util.updateStageLocationToPlayer();
						if (SongPlayer.showFakePlayer) { //check if fakeplayer is enabled. if it is, spawn the fakeplayer to the stage
							if (SongPlayer.fakePlayer == null) {
								SongPlayer.fakePlayer = new FakePlayerEntity();
							}
							SongPlayer.fakePlayer.copyStagePosAndPlayerLook();
						}
					}
					SongPlayer.addChatMessage("§6Changed method to survival only");
					break;*/
				}
				case "gamemode": {
					if (SongPlayer.switchGamemode) {
						SongPlayer.addChatMessage("§6You are already using this method of playing songs");
						return true;
					}
					if (SongHandler.getInstance().stage != null) {
						Util.updateStageLocationToPlayer();
						if (SongPlayer.showFakePlayer) { //check if fakeplayer is enabled. if it is, spawn the fakeplayer to the stage
							if (SongPlayer.fakePlayer == null) {
								SongPlayer.fakePlayer = new FakePlayerEntity();
							}
							SongPlayer.fakePlayer.copyStagePosAndPlayerLook();
						}
					}

					ModProperties.getInstance().updateValue("useCommandsForPlaying", String.valueOf(false));
					ModProperties.getInstance().updateValue("switchGamemode", String.valueOf(true));
					Util.updateValuesToConfig();
					//if (SongHandler.getInstance().currentSong != null) {
					//	SongHandler.getInstance().stage.checkBuildStatus(SongHandler.getInstance().currentSong);
					//}
					Util.disableFlightIfNeeded();
					if (SongPlayer.showFakePlayer && SongPlayer.fakePlayer != null) { //fake player is showing
						SongPlayer.removeFakePlayer();
					}
					SongPlayer.addChatMessage("§6Changed method to switching gamemode");
					if (SongHandler.getInstance().currentSong != null) { //player is in the middle of a song
						Util.enableFlightIfNeeded();
						SongHandler.getInstance().stage.checkBuildStatus(SongHandler.getInstance().currentSong);
						if (SongPlayer.MC.interactionManager.getCurrentGameMode() != GameMode.SURVIVAL) {
							SongPlayer.MC.getNetworkHandler().sendCommand(SongPlayer.survivalCommand);
						}
						//SongPlayer.addChatMessage("stage needs rebuilt");
						//if (SongPlayer.MC.interactionManager.getCurrentGameMode() != GameMode.CREATIVE) {
						//	SongPlayer.MC.getNetworkHandler().sendCommand(SongPlayer.creativeCommand);
						//}
						//SongHandler.getInstance().building = true;
						//stage does not need rebuilt. send player into survival mod if needed.
					}

					return true;
				}
				case "client": {
					if (SongPlayer.switchGamemode) {
						if (SongHandler.getInstance().stage != null) {
							SongHandler.getInstance().stage.movePlayerToStagePosition(true, false, false);
						}
					}
					ModProperties.getInstance().updateValue("useCommandsForPlaying", String.valueOf(false));
					ModProperties.getInstance().updateValue("switchGamemode", String.valueOf(false));
					if (SongPlayer.showFakePlayer && SongPlayer.fakePlayer != null) { //fake player is showing
						SongPlayer.removeFakePlayer();
					}
					Util.disableFlightIfNeeded();
					Util.updateValuesToConfig();
					SongPlayer.addChatMessage("§6Changed method to client-side");
					return true;
				}
				default: {
					return false;
				}
			}
		}
		public CompletableFuture<Suggestions> getSuggestions(String args, SuggestionsBuilder suggestionsBuilder) {
			String[] theArgs = args.split(" ");
			int argumentint = theArgs.length;

			if (args.endsWith(" ")) {
				argumentint += 1;
			}
			switch(argumentint) {
				case 0:
				case 1: {
					//possibleArguments.add("survival");
					possibleArguments.add("gamemode");
					possibleArguments.add("commands");
					possibleArguments.add("client");
					break;
				}
				default: {
					return null;
				}
			}
			return CommandSource.suggestMatching(possibleArguments, suggestionsBuilder);
		}
	}

	private static class prefixCommand extends Command {

		@Override
		public String getName() {
			return "prefix";
		}

		@Override
		public String getSyntax() {
			return SongPlayer.prefix + "prefix [prefix]";
		}

		@Override
		public String getDescription() {
			return "Change the prefix used to use these commands.";
		}

		@Override
		public boolean processCommand(String args) {
			String[] arguments = args.toLowerCase().split(" ");
			if (!(arguments.length == 1)) {
				return false;
			}

			String newprefix = arguments[0].toLowerCase();

			if (newprefix.length() > 20) {
				SongPlayer.addChatMessage("§cprefix can only be up to 20 characters long!");
				return true;
			} else if (newprefix.isEmpty()) {
				return false;
			} else if (newprefix.startsWith("/")) {
				SongPlayer.addChatMessage("§cprefix can not start with §4/");
				return true;
			} else if (newprefix.contains(" ")) {
				SongPlayer.addChatMessage("§cprefix can not contain spaces");
				return true;
			}
			ModProperties.getInstance().updateValue("prefix", newprefix);
			Util.updateValuesToConfig();
			SongPlayer.addChatMessage("§6prefix is set to: §3" + newprefix);
			return true;
		}
	}

	private static class toggleCommand extends Command {

		@Override
		public String getName() {
			return "toggle";
		}

		@Override
		public String getSyntax() {
			return SongPlayer.prefix + "toggle [swing, rotate, allMovements] [true, false]";
		}

		@Override
		public String getDescription() {
			return "Allows you to toggle swing and rotation packets [Increases the chances of you exceeding packet limit!]";
		}

		@Override
		public boolean processCommand(String args) {
			String[] arguments = args.toLowerCase().split(" ");

			if (!(arguments.length == 2)) {
				return false;
			}

			boolean toggleto;

			switch(arguments[1].toLowerCase()) {
				case "true": {
					toggleto = true;
					break;
				}
				case "false": {
					toggleto = false;
					break;
				}
				default: {
					return false;
				}
			}

			switch(arguments[0].toLowerCase()) {
				case "rotate": {
					ModProperties.getInstance().updateValue("rotate", toggleto + "");
					Util.updateValuesToConfig();
					SongPlayer.addChatMessage("§6set rotate to " + toggleto);
					return true;
				}
				case "swing": {
					ModProperties.getInstance().updateValue("swing", toggleto + "");
					Util.updateValuesToConfig();
					SongPlayer.addChatMessage("§6set swing to " + toggleto);
					return true;
				}
				case "allmovements": {
					ModProperties.getInstance().updateValue("swing", toggleto + "");
					ModProperties.getInstance().updateValue("rotate", toggleto + "");
					Util.updateValuesToConfig();
					SongPlayer.addChatMessage("§6set swing and rotate to " + toggleto);
					return true;
				}
				/*
				case "usecommandstoplay": {
					Boolean oldvalue = Boolean.parseBoolean(ModProperties.getInstance().getConfig().getProperty("useCommandsForPlaying"));
					ModProperties.getInstance().updateValue("useCommandsForPlaying", toggleto + "");
					Util.updateValuesToConfig();
					SongPlayer.addChatMessage("§6set useCommandsToPlay to " + toggleto);
					if (oldvalue == toggleto) { //prevent my crappy spaghetti code from breaking
						return true;
					}
					if (!toggleto) { //updating to false
						Util.enableFlightIfNeeded();
						if (SongHandler.getInstance().stage != null) {
							Util.updateStageLocationToPlayer();
							if (SongPlayer.showFakePlayer) { //check if fakeplayer is enabled. if it is, spawn the fakeplayer to the stage
								if (SongPlayer.fakePlayer == null) {
									SongPlayer.fakePlayer = new FakePlayerEntity();
								}
								SongPlayer.fakePlayer.copyStagePosAndPlayerLook();
							}
						}
					} else { //updating to true
						Util.disableFlightIfNeeded();
						if (SongPlayer.showFakePlayer && SongPlayer.fakePlayer != null) { //fake player is showing
							SongPlayer.removeFakePlayer();
						}
						if (SongHandler.getInstance().stage != null) {
							SongHandler.getInstance().stage.movePlayerToStagePosition(true, false, false);
						}
						if (SongHandler.getInstance().currentSong != null) {
							Util.enableFlightIfNeeded();
						}
					}
					return true;
				}
				 */
				default: {
					return false;
				}
			}
		}
		public CompletableFuture<Suggestions> getSuggestions(String args, SuggestionsBuilder suggestionsBuilder) {
			String[] theArgs = args.split(" ");
			int argumentint = theArgs.length;

			if (args.endsWith(" ")) {
				argumentint += 1;
			}

			switch(argumentint) {
				case 0:
				case 1: {
					possibleArguments.add("allMovements");
					possibleArguments.add("rotate");
					possibleArguments.add("swing");
					break;
				}
				case 2: {
					possibleArguments.add("true");
					possibleArguments.add("false");
					break;
				}
				default: {
					return null;
				}
			}

			return CommandSource.suggestMatching(possibleArguments, suggestionsBuilder);
		}
	}

	private static class helpCommand extends Command {
    	public String getName() {
    		return "help";
    	}
    	public String getSyntax() {
    		return SongPlayer.prefix + "help [command]";
    	}
    	public String getDescription() {
    		return "Lists commands or explains command";
    	}
    	public boolean processCommand(String args) {
    		if (args.length() == 0) {
    			StringBuilder helpMessage = new StringBuilder("§6Commands -");
    			for (Command c : commands) {
    				helpMessage.append(" " + SongPlayer.prefix + c.getName());
    			}
    			SongPlayer.addChatMessage(helpMessage.toString());
    		}
    		else {
				if (args.contains(" ")) {
					return false;
				}
				if (!commandMap.containsKey(args.toLowerCase())) {
					SongPlayer.addChatMessage("§cCommand not recognized: " + args);
					return true;
				}
				Command c = commandMap.get(args.toLowerCase());
				SongPlayer.addChatMessage("§6------------------------------");
				SongPlayer.addChatMessage("§6Help: §3" + c.getName());
				SongPlayer.addChatMessage("§6Description: §3" + c.getDescription());
				SongPlayer.addChatMessage("§6Usage: §3" + c.getSyntax());
				if (c.getAliases().length > 0) {
					SongPlayer.addChatMessage("§6Aliases: §3" + String.join(", ", c.getAliases()));
				}
				SongPlayer.addChatMessage("§6------------------------------");
    		}
			return true;
    	}
		public CompletableFuture<Suggestions> getSuggestions(String args, SuggestionsBuilder suggestionsBuilder) {
			String[] theArgs = args.split(" ");
			int argumentint = theArgs.length;

			if (args.endsWith(" ")) {
				argumentint += 1;
			}
			if (argumentint > 1) {
				return null;
			}
			return CommandSource.suggestMatching(commandCompletions, suggestionsBuilder);
		}
	}

	private static class playCommand extends Command {
    	public String getName() {
    		return "play";
    	}
    	public String getSyntax() {
    		return  SongPlayer.prefix + "play <song or url>";
    	}
    	public String getDescription() {
    		return "Plays a song";
    	}
    	public boolean processCommand(String args) {
			if (!Util.playlistSongs.isEmpty() || !Util.currentPlaylist.isEmpty()) {
				SongPlayer.addChatMessage("§cYou cannot use this command when a playlist is running! If you want to run an individual song, please type \"" + SongPlayer.prefix + "stop\" and try again.");
				return true;
			}
    		if (args.length() > 0) {
				SongHandler.getInstance().loadSong(args, SongPlayer.SONG_DIR);
    			return true;
    		}
			return false;
    	}
		public CompletableFuture<Suggestions> getSuggestions(String args, SuggestionsBuilder suggestionsBuilder) {
			String[] theArgs = args.split(" ");
			for (String a : theArgs) {
				if (a.endsWith(".mid") || a.endsWith(".midi") || a.endsWith(".nbs")) {
					return null;
				}
			}
			try {
				List<String> filenames = Arrays.stream(SongPlayer.SONG_DIR.listFiles())
						.filter(File::isFile)
						.map(File::getName)
						.collect(Collectors.toList());
				return CommandSource.suggestMatching(filenames, suggestionsBuilder);
			} catch(NullPointerException e) {
				if (!SongPlayer.SONG_DIR.exists()) {
					SongPlayer.SONG_DIR.mkdir();
				}
				return null;
			}
		}
    }

	private static class stopCommand extends Command {
    	public String getName() {
    		return "stop";
    	}
    	public String getSyntax() {
    		return SongPlayer.prefix + "stop";
    	}
    	public String getDescription() {
    		return "Stops playing";
    	}
    	public boolean processCommand(String args) {
			if (args.length() != 0) {
				return false;
			}
			if (SongHandler.getInstance().currentSong == null && SongHandler.getInstance().songQueue.isEmpty()) {
				SongPlayer.addChatMessage("§6No song is currently playing");
				Util.currentPlaylist = "";
				Util.playlistSongs.clear();
				return true;
			}
			if (Util.currentPlaylist.isEmpty()) {
				SongPlayer.addChatMessage("§6Stopped playing");
			} else {
				SongPlayer.addChatMessage("§6Stopped playlist §3" + Util.currentPlaylist);
				Util.currentPlaylist = "";
			}
			if (SongHandler.getInstance().stage != null) {
				SongHandler.getInstance().stage.movePlayerToStagePosition();
			}

			SongHandler.getInstance().cleanup(true);
			Util.disableFlightIfNeeded();

			if (SongHandler.oldItemHeld != null) {
				PlayerInventory inventory = SongPlayer.MC.player.getInventory();
				inventory.setStack(inventory.selectedSlot, SongHandler.oldItemHeld);
				SongPlayer.MC.interactionManager.clickCreativeStack(SongPlayer.MC.player.getStackInHand(Hand.MAIN_HAND), 36 + inventory.selectedSlot);
				SongHandler.oldItemHeld = null;
			}

			return true;
    	}
    }

	private static class skipCommand extends Command {
		public String getName() {
			return "skip";
		}
		public String getSyntax() {
			return SongPlayer.prefix + "skip";
		}
		public String getDescription() {
			return "Skips current song";
		}
		public boolean processCommand(String args) {
			if (SongHandler.getInstance().currentSong == null) {
				SongPlayer.addChatMessage("§6No song is currently playing");
				return true;
			}
			if (args.length() == 0) {
				Util.playcooldown = Calendar.getInstance().getTime().getTime() + 1500;
				SongHandler.getInstance().currentSong = null;
				Util.disableFlightIfNeeded();
				Util.advancePlaylist();
				return true;
			}
			return false;
		}
	}

	private static class gotoCommand extends Command {
    	public String getName() {
    		return "goto";
    	}
    	public String getSyntax() {
    		return SongPlayer.prefix + "goto <mm:ss>";
    	}
    	public String getDescription() {
    		return "Goes to a specific time in the song";
    	}
    	public boolean processCommand(String args) {
			if (SongHandler.getInstance().currentSong == null) {
				SongPlayer.addChatMessage("§6No song is currently playing");
				return true;
			}

    		if (args.length() > 0) {
				try {
					long time = Util.parseTime(args);
					SongHandler.getInstance().currentSong.setTime(time);
					SongPlayer.addChatMessage("§6Set song time to §3" + Util.formatTime(time));
					return true;
				} catch (IOException e) {
					SongPlayer.addChatMessage("§cNot a valid time stamp");
					return false;
				}
    		}
    		else {
    			return false;
    		}
    	}
    }

	private static class loopCommand extends Command {
    	public String getName() {
    		return "loop";
    	}
    	public String getSyntax() {
    		return SongPlayer.prefix + "loop";
    	}
    	public String getDescription() {
    		return "Toggles song looping";
    	}
    	public boolean processCommand(String args) {
    		if (SongHandler.getInstance().currentSong == null) {
				SongPlayer.addChatMessage("§6No song is currently playing");
				return true;
			}
    		boolean toggledto;
			if (Util.currentPlaylist.isEmpty()) { //toggle looping induvidual song
				SongHandler.getInstance().currentSong.looping = !SongHandler.getInstance().currentSong.looping;
				SongHandler.getInstance().currentSong.loopCount = 0;
				toggledto = SongHandler.getInstance().currentSong.looping;
			} else { //toggle looping playlist
				Util.loopPlaylist = !Util.loopPlaylist;
				toggledto = Util.loopPlaylist;
			}
			if (toggledto) {
				SongPlayer.addChatMessage("§6Enabled looping");
			} else {
				SongPlayer.addChatMessage("§6Disabled looping");
			}
			return true;
    	}
    }

	private static class statusCommand extends Command {
    	public String getName() {
    		return "status";
    	}
		public String[] getAliases() {
			return new String[]{"current"};
		}
    	public String getSyntax() {
    		return SongPlayer.prefix + "status";
    	}
    	public String getDescription() {
    		return "Gets the status of the song or playlist that is currently playing";
    	}
    	public boolean processCommand(String args) {
    		if (args.length() != 0) {
				return false;
			}
			if (SongHandler.getInstance().currentSong == null) {
				SongPlayer.addChatMessage("§6No song is currently playing");
				return true;
			}
			Song currentSong = SongHandler.getInstance().currentSong;
			long currentTime = Math.min(currentSong.time, currentSong.length);
			long totalTime = currentSong.length;
			if (Util.currentPlaylist.isEmpty()) {
				SongPlayer.addChatMessage(String.format("§6Currently playing §3%s §9(%s/%s) §6looping: §3%s", currentSong.name, Util.formatTime(currentTime), Util.formatTime(totalTime), Util.loopPlaylist));
			} else {
				SongPlayer.addChatMessage(String.format("§6Currently playing playlist §3%s §9(%s/%s) §6looping: §3%s", Util.currentPlaylist, Util.playlistIndex, Util.playlistSongs.size(), Util.loopPlaylist));
			}
			return true;
		}
    }

	private static class queueCommand extends Command {
		public String getName() {
			return "queue";
		}
		public String[] getAliases() {
			return new String[]{"showQueue"};
		}
		public String getSyntax() {
			return SongPlayer.prefix + "queue";
		}
		public String getDescription() {
			return "Shows the current song queue";
		}
		public boolean processCommand(String args) {
			if (args.length() > 0) {
				return false;
			}
			int index = 0;
			if (!Util.currentPlaylist.isEmpty()) { //status on playlist
				SongPlayer.addChatMessage("§6------------------------------");
				SongPlayer.addChatMessage("§6Current playlist: §3" + Util.currentPlaylist);
				for (String song : Util.playlistSongs) {
					index++;
					if (SongHandler.getInstance().currentSong.name.equals(song)) {
						SongPlayer.addChatMessage("  §6" + index + ". §3" + song + "§9 (playing)");
					} else {
						SongPlayer.addChatMessage("  §6" + index + ". §3" + song);
					}
				}
				SongPlayer.addChatMessage("§6------------------------------");
				return true;
			}
			if (SongHandler.getInstance().currentSong == null && SongHandler.getInstance().songQueue.isEmpty()) {
				SongPlayer.addChatMessage("§6No song is currently playing");
				return true;
			}

			SongPlayer.addChatMessage("§6------------------------------");
			if (SongHandler.getInstance().currentSong != null) {
				SongPlayer.addChatMessage("§6Current song: §3" + SongHandler.getInstance().currentSong.name);
			}
			for (Song song : SongHandler.getInstance().songQueue) {
				index++;
				SongPlayer.addChatMessage(String.format("§6%d. §3%s", index, song.name));
			}
			SongPlayer.addChatMessage("§6------------------------------");
			return true;
		}
	}

	private static class songsCommand extends Command {
    	public String getName() {
    		return "songs";
    	}
		public String[] getAliases() {
			return new String[]{"list"};
		}
    	public String getSyntax() {
    		return SongPlayer.prefix + "songs";
    	}
    	public String getDescription() {
    		return "Lists available songs";
    	}
    	public boolean processCommand(String args) {
    		if (args.length() == 0) {
				StringBuilder sb = new StringBuilder("§6");
				boolean firstItem = true;
    			for (File songFile : SongPlayer.SONG_DIR.listFiles()) {
        			String fileName = songFile.getName();
        			if (firstItem) {
        				firstItem = false;
        			}
        			else {
        				sb.append(", ");
        			}
    				sb.append(fileName);
        		}
    			SongPlayer.addChatMessage(sb.toString());
    			return true;
    		}
    		else {
    			return false;
    		}
    	}
    }

	private static class setCreativeCommandCommand extends Command {
    	public String getName() {
    		return "setCreativeCommand";
    	}
		public String[] getAliases() {
			return new String[]{"sc"};
		}
    	public String getSyntax() {
    		return SongPlayer.prefix + "setCreativeCommand <command>";
    	}
    	public String getDescription() {
    		return "Sets the command used to go into creative mode";
    	}
    	public boolean processCommand(String args) {
    		if (args.length() > 0) {
				if (args.startsWith("/")) {
					args = args.substring(1);
				}
				ModProperties.getInstance().updateValue("creativeCommand", args);
				Util.updateValuesToConfig();
    			SongPlayer.addChatMessage("§6Set creative command to §3/" + args);
				return true;
    		}
    		else {
    			return false;
    		}
    	}
    }

	private static class setSurvivalCommandCommand extends Command {
    	public String getName() {
    		return "setSurvivalCommand";
    	}
		public String[] getAliases() {
			return new String[]{"ss"};
		}
    	public String getSyntax() {
    		return SongPlayer.prefix + "setSurvivalCommand <command>";
    	}
    	public String getDescription() {
    		return "Sets the command used to go into survival mode";
    	}
    	public boolean processCommand(String args) {
			if (args.length() > 0) {
				if (args.startsWith("/")) {
					args = args.substring(1);
				}
				ModProperties.getInstance().updateValue("survivalCommand", args);
				Util.updateValuesToConfig();
				SongPlayer.addChatMessage("§6Set survival command to §3/" + args);
				return true;
			}
			else {
				return false;
			}
    	}
    }

	private static class useEssentialsCommandsCommand extends Command {
		public String getName() {
			return "useEssentialsCommands";
		}
		public String[] getAliases() {
			return new String[]{"essentials", "useEssentials", "essentialsCommands"};
		}
		public String getSyntax() {
			return SongPlayer.prefix + "useEssentialsCommands";
		}
		public String getDescription() {
			return "Switches to using essentials gamemode commands";
		}
		public boolean processCommand(String args) {
			if (args.length() == 0) {
				ModProperties.getInstance().updateValue("creativeCommand", "gmc");
				ModProperties.getInstance().updateValue("survivalCommand", "gms");
				Util.updateValuesToConfig();
				SongPlayer.addChatMessage("§6Now using essentials gamemode commands");
				return true;
			}
			else {
				return false;
			}
		}
	}

	private static class useVanillaCommandsCommand extends Command {
		public String getName() {
			return "useVanillaCommands";
		}
		public String[] getAliases() {
			return new String[]{"vanilla", "useVanilla", "vanillaCommands"};
		}
		public String getSyntax() {
			return SongPlayer.prefix + "useVanillaCommands";
		}
		public String getDescription() {
			return "Switches to using vanilla gamemode commands";
		}
		public boolean processCommand(String args) {
			if (args.length() == 0) {
				ModProperties.getInstance().updateValue("creativeCommand", "gamemode creative");
				ModProperties.getInstance().updateValue("survivalCommand", "gamemode survival");
				Util.updateValuesToConfig();
				SongPlayer.addChatMessage("§6Now using vanilla gamemode commands");
				return true;
			}
			else {
				return false;
			}
		}
	}

	private static class toggleFakePlayerCommand extends Command {
		public String getName() {
    		return "toggleFakePlayer";
    	}
		public String[] getAliases() {
			return new String[]{"fakePlayer", "fp"};
		}
    	public String getSyntax() {
    		return SongPlayer.prefix + "toggleFakePlayer";
    	}
    	public String getDescription() {
    		return "Shows a fake player representing your true position when playing songs";
    	}
    	public boolean processCommand(String args) {
    		if (args.length() != 0) {
				return false;
			}
			SongPlayer.showFakePlayer = !SongPlayer.showFakePlayer;
			if (!SongPlayer.showFakePlayer) {
				SongPlayer.addChatMessage("§6Disabled fake player");
				return true;
			}
			SongPlayer.addChatMessage("§6Enabled fake player");
			if (SongPlayer.switchGamemode) {
				if (SongHandler.getInstance().stage != null) {
					SongPlayer.fakePlayer = new FakePlayerEntity();
					SongPlayer.fakePlayer.copyStagePosAndPlayerLook();
				}
			}
			return true;

    	}
	}

	private static class testSongCommand extends Command {
		public String getName() {
			return "testSong";
		}
		public String getSyntax() {
			return SongPlayer.prefix + "testSong";
		}
		public String getDescription() {
			return "Creates a song for testing";
		}
		public boolean processCommand(String args) {
			if (args.length() != 0) {
				return false;
			}
			if (!Util.playlistSongs.isEmpty() || !Util.currentPlaylist.isEmpty()) {
				SongPlayer.addChatMessage("§cYou cannot use this command when a playlist is running! If you want to run an individual song, please type \"" + SongPlayer.prefix + "stop\" and try again.");
				return true;
			}
			Song song = new Song("test_song");
			for (int i=0; i<400; i++) {
				song.add(new Note(i, i*50));
			}
			song.length = 400*50;
			SongHandler.getInstance().setSong(song);
			return true;
		}
	}

	// $ prefix included in command string
	public static CompletableFuture<Suggestions> handleSuggestions(String text, SuggestionsBuilder suggestionsBuilder) {
		if (!text.contains(" ")) {
			List<String> names = commandCompletions
					.stream()
					.map((commandName) -> SongPlayer.prefix + commandName)
					.collect(Collectors.toList());
			return CommandSource.suggestMatching(names, suggestionsBuilder);
		}

		String[] split = text.split(" ");
		if (!split[0].startsWith(SongPlayer.prefix)) {
			return null;
		}

		String commandName = split[0].substring(SongPlayer.prefix.length()).toLowerCase();
		if (!commandMap.containsKey(commandName)) {
			return null;
		}

		//split.length == 1 ? "" : split[1]
		//String.join(" ", Arrays.copyOfRange(split, 1, split.length))
		String[] cmdargs = text.split(" ", 2);
		possibleArguments.clear();
		return commandMap.get(commandName).getSuggestions(cmdargs[1], suggestionsBuilder);
	}
}
