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

import java.lang.reflect.Field;

import org.bukkit.craftbukkit.libs.joptsimple.OptionSet;
import org.bukkit.craftbukkit.v1_11_R1.CraftServer;
import org.bukkit.plugin.PluginManager;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import net.minecraft.server.v1_11_R1.DataConverterManager;
import net.minecraft.server.v1_11_R1.DedicatedServer;
import net.minecraft.server.v1_11_R1.PlayerList;
import net.minecraft.server.v1_11_R1.UserCache;

/**
 * @author mepeisen
 *
 */
public class SpigotDedicatedServer extends DedicatedServer
{

    /**
     * @param options
     * @param dataconvertermanager
     * @param yggdrasilauthenticationservice
     * @param minecraftsessionservice
     * @param gameprofilerepository
     * @param usercache
     */
    public SpigotDedicatedServer(OptionSet options, DataConverterManager dataconvertermanager, YggdrasilAuthenticationService yggdrasilauthenticationservice,
            MinecraftSessionService minecraftsessionservice, GameProfileRepository gameprofilerepository, UserCache usercache)
    {
        super(options, dataconvertermanager, yggdrasilauthenticationservice, minecraftsessionservice, gameprofilerepository, usercache);
    }

    @Override
    public void a(PlayerList playerlist)
    {
        super.a(playerlist);

        try
        {
            final Field field = CraftServer.class.getDeclaredField("pluginManager"); //$NON-NLS-1$
            field.setAccessible(true);
            final PluginManager orig = (PluginManager) field.get(this.server);
            field.set(this.server, new ExtendedPluginManager(orig));
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
    }
}
