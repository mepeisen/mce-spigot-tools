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
import org.bukkit.craftbukkit.v1_8_R2.CraftServer;
import org.bukkit.plugin.PluginManager;

import net.minecraft.server.v1_8_R2.DedicatedServer;
import net.minecraft.server.v1_8_R2.PlayerList;

/**
 * @author mepeisen
 *
 */
public class SpigotDedicatedServer extends DedicatedServer
{

    /**
     * @param options
     */
    public SpigotDedicatedServer(OptionSet options)
    {
        super(options);
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
