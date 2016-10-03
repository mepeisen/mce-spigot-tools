/*
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package eu.xworlds.mceclipse.spigot;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.craftbukkit.libs.jline.TerminalFactory;
import org.bukkit.craftbukkit.libs.jline.UnsupportedTerminal;
import org.bukkit.craftbukkit.libs.joptsimple.OptionException;
import org.bukkit.craftbukkit.libs.joptsimple.OptionParser;
import org.bukkit.craftbukkit.libs.joptsimple.OptionSet;
import org.bukkit.craftbukkit.v1_10_R1.CraftServer;
import org.fusesource.jansi.AnsiConsole;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import net.minecraft.server.v1_10_R1.DispenserRegistry;
import net.minecraft.server.v1_10_R1.MinecraftServer;
import net.minecraft.server.v1_10_R1.UserCache;
import net.minecraft.server.v1_10_R1.DataConverterRegistry;
import net.minecraft.server.v1_10_R1.DedicatedServer;

/**
 * Main starter for spigot.
 * 
 * @author mepeisen
 */
public class Main extends org.bukkit.craftbukkit.Main
{
    
    /**
     * @param args
     */
    public static void main(String[] args)
    {
        OptionParser parser = new OptionParser() {
            {
                acceptsAll(asList("?", "help"), "Show the help");
                
                acceptsAll(asList("c", "config"), "Properties file to use").withRequiredArg().ofType(File.class).defaultsTo(new File("server.properties")).describedAs("Properties file");
                
                acceptsAll(asList("P", "plugins"), "Plugin directory to use").withRequiredArg().ofType(File.class).defaultsTo(new File("plugins")).describedAs("Plugin directory");
                
                acceptsAll(asList("h", "host", "server-ip"), "Host to listen on").withRequiredArg().ofType(String.class).describedAs("Hostname or IP");
                
                acceptsAll(asList("W", "world-dir", "universe", "world-container"), "World container").withRequiredArg().ofType(File.class).describedAs("Directory containing worlds");
                
                acceptsAll(asList("w", "world", "level-name"), "World name").withRequiredArg().ofType(String.class).describedAs("World name");
                
                acceptsAll(asList("p", "port", "server-port"), "Port to listen on").withRequiredArg().ofType(Integer.class).describedAs("Port");
                
                acceptsAll(asList("o", "online-mode"), "Whether to use online authentication").withRequiredArg().ofType(Boolean.class).describedAs("Authentication");
                
                acceptsAll(asList("s", "size", "max-players"), "Maximum amount of players").withRequiredArg().ofType(Integer.class).describedAs("Server size");
                
                acceptsAll(asList("d", "date-format"), "Format of the date to display in the console (for log entries)").withRequiredArg().ofType(SimpleDateFormat.class)
                        .describedAs("Log date format");
                
                acceptsAll(asList("log-pattern"), "Specfies the log filename pattern").withRequiredArg().ofType(String.class).defaultsTo("server.log").describedAs("Log filename");
                
                acceptsAll(asList("log-limit"), "Limits the maximum size of the log file (0 = unlimited)").withRequiredArg().ofType(Integer.class).defaultsTo(0).describedAs("Max log size");
                
                acceptsAll(asList("log-count"), "Specified how many log files to cycle through").withRequiredArg().ofType(Integer.class).defaultsTo(1).describedAs("Log count");
                
                acceptsAll(asList("log-append"), "Whether to append to the log file").withRequiredArg().ofType(Boolean.class).defaultsTo(true).describedAs("Log append");
                
                acceptsAll(asList("log-strip-color"), "Strips color codes from log file");
                
                acceptsAll(asList("b", "bukkit-settings"), "File for bukkit settings").withRequiredArg().ofType(File.class).defaultsTo(new File("bukkit.yml")).describedAs("Yml file");
                
                acceptsAll(asList("C", "commands-settings"), "File for command settings").withRequiredArg().ofType(File.class).defaultsTo(new File("commands.yml")).describedAs("Yml file");
                
                acceptsAll(asList("nojline"), "Disables jline and emulates the vanilla console");
                
                acceptsAll(asList("noconsole"), "Disables the console");
                
                acceptsAll(asList("v", "version"), "Show the CraftBukkit Version");
                
                acceptsAll(asList("demo"), "Demo mode");
                
                acceptsAll(asList("S", "spigot-settings"), "File for spigot settings")
                .withRequiredArg()
                .ofType(File.class)
                .defaultsTo(new File("spigot.yml"))
                .describedAs("Yml file");
            }
        };
        
        OptionSet options = null;
        
        try
        {
            options = parser.parse(args);
        }
        catch (OptionException ex)
        {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, ex.getLocalizedMessage());
        }
        
        if ((options == null) || (options.has("?")))
        {
            try
            {
                parser.printHelpOn(System.out);
            }
            catch (IOException ex)
            {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else if (options.has("v"))
        {
            System.out.println(CraftServer.class.getPackage().getImplementationVersion());
        }
        else
        {
            // Do you love Java using + and ! as string based identifiers? I sure do!
            String path = new File(".").getAbsolutePath();
            if (path.contains("!") || path.contains("+"))
            {
                System.err.println("Cannot run server in a directory with ! or + in the pathname. Please rename the affected folders and try again.");
                return;
            }
            
            try
            {
                // This trick bypasses Maven Shade's clever rewriting of our getProperty call when using String literals
                String jline_UnsupportedTerminal = new String(
                        new char[] { 'j', 'l', 'i', 'n', 'e', '.', 'U', 'n', 's', 'u', 'p', 'p', 'o', 'r', 't', 'e', 'd', 'T', 'e', 'r', 'm', 'i', 'n', 'a', 'l' });
                String jline_terminal = new String(new char[] { 'j', 'l', 'i', 'n', 'e', '.', 't', 'e', 'r', 'm', 'i', 'n', 'a', 'l' });
                
                useJline = !(jline_UnsupportedTerminal).equals(System.getProperty(jline_terminal));
                
                if (options.has("nojline"))
                {
                    System.setProperty("user.language", "en");
                    useJline = false;
                }
                
                if (useJline)
                {
                    AnsiConsole.systemInstall();
                }
                else
                {
                    // This ensures the terminal literal will always match the jline implementation
                    System.setProperty(TerminalFactory.JLINE_TERMINAL, UnsupportedTerminal.class.getName());
                }
                
                if (options.has("noconsole"))
                {
                    useConsole = false;
                }
                
                // Spigot Start
                int maxPermGen = 0; // In kb
                for (String s : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())
                {
                    if (s.startsWith("-XX:MaxPermSize"))
                    {
                        maxPermGen = Integer.parseInt(s.replaceAll("[^\\d]", ""));
                        maxPermGen <<= 10 * ("kmg".indexOf(Character.toLowerCase(s.charAt(s.length() - 1))));
                    }
                }
                if (Float.parseFloat(System.getProperty("java.class.version")) < 52 && maxPermGen < (128 << 10)) // 128mb
                {
                    System.out.println("Warning, your max perm gen size is not set or less than 128mb. It is recommended you restart Java with the following argument: -XX:MaxPermSize=128M");
                    System.out.println("Please see http://www.spigotmc.org/wiki/changing-permgen-size/ for more details and more in-depth instructions.");
                }
                // Spigot End
                System.out.println("Loading libraries, please wait...");
                minecraftMain(options);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        }
    }
    
    private static void minecraftMain(OptionSet options)
    {
        DispenserRegistry.c();
        try
        {
            String s1 = ".";
            YggdrasilAuthenticationService yggdrasilauthenticationservice = new YggdrasilAuthenticationService(
                    Proxy.NO_PROXY, UUID.randomUUID().toString());
            MinecraftSessionService minecraftsessionservice = yggdrasilauthenticationservice
                    .createMinecraftSessionService();
            GameProfileRepository gameprofilerepository = yggdrasilauthenticationservice.createProfileRepository();
            UserCache usercache = new UserCache(gameprofilerepository, new File(s1, MinecraftServer.a.getName()));
            
            DedicatedServer dedicatedserver = new SpigotDedicatedServer(options, DataConverterRegistry.a(), yggdrasilauthenticationservice, minecraftsessionservice, gameprofilerepository, usercache);
            
            if (options.has("port"))
            {
                int port = ((Integer) options.valueOf("port")).intValue();
                if (port > 0)
                {
                    dedicatedserver.setPort(port);
                }
            }
            
            if (options.has("universe"))
            {
                dedicatedserver.universe = ((File) options.valueOf("universe"));
            }
            
            if (options.has("world"))
            {
                dedicatedserver.setWorld((String) options.valueOf("world"));
            }
            
            dedicatedserver.primaryThread.start();
        }
        catch (Exception exception)
        {
            // MinecraftServer.LOGGER.fatal("Failed to start the minecraft server", exception);
            // TODO Logger
            exception.printStackTrace();
        }
    }
    
    private static List<String> asList(String... params)
    {
        return Arrays.asList(params);
    }
    
}
