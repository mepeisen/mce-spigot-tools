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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.UnknownDependencyException;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * A loader to load spigot plugins from eclipse projects.
 * 
 * @author mepeisen
 */
public class ProjectPluginLoader implements PluginLoader
{
    
    /** the server. */
    private Server                      server;
    
    /** the plugin file filters. */
    private final Pattern[]             fileFilters = { Pattern.compile("\\.eclipseproject$") }; //$NON-NLS-1$
    
    private Map<String, Class<?>>       classes;
    private Map<String, URLClassLoader> loaders;
    private final List<FakeClassLoader> fakeLoaders = new ArrayList<>();
    
    private JavaPluginLoader            javaLoader;
    
    static final boolean debugcl = false;
    
    /**
     * Constructor
     * 
     * @param instance
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public ProjectPluginLoader(Server instance) throws Exception
    {
        Validate.notNull(instance, "Server cannot be null"); //$NON-NLS-1$
        this.server = instance;
        
        final PluginManager mng = ((ExtendedPluginManager)Bukkit.getServer().getPluginManager()).getDelegate();
        final Field fileAssocField = mng.getClass().getDeclaredField("fileAssociations"); //$NON-NLS-1$
        fileAssocField.setAccessible(true);
        final Map<Pattern, PluginLoader> fileAssociations = (Map<Pattern, PluginLoader>) fileAssocField.get(mng);
        
        for (final PluginLoader loader : fileAssociations.values())
        {
            if (loader instanceof JavaPluginLoader)
            {
                this.javaLoader = (JavaPluginLoader) loader;
                final Field classesField = JavaPluginLoader.class.getDeclaredField("classes"); //$NON-NLS-1$
                classesField.setAccessible(true);
                this.classes = (Map<String, Class<?>>) classesField.get(this.javaLoader);
                
                final Field loadersField = JavaPluginLoader.class.getDeclaredField("loaders"); //$NON-NLS-1$
                loadersField.setAccessible(true);
                this.loaders = (Map<String, URLClassLoader>) loadersField.get(this.javaLoader);
            }
        }
        
        Validate.notNull(this.javaLoader, "javaLoader cannot be null"); //$NON-NLS-1$
        Validate.notNull(this.classes, "classes cannot be null"); //$NON-NLS-1$
        Validate.notNull(this.loaders, "loaders cannot be null"); //$NON-NLS-1$
    }
    
    @Override
    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Listener listener, Plugin plugin)
    {
        return this.javaLoader.createRegisteredListeners(listener, plugin);
    }
    
    @Override
    public void disablePlugin(Plugin plugin)
    {
        this.javaLoader.disablePlugin(plugin);
    }
    
    @Override
    public void enablePlugin(Plugin plugin)
    {
        this.javaLoader.enablePlugin(plugin);
    }
    
    @Override
    public Pattern[] getPluginFileFilters()
    {
        return this.fileFilters.clone();
    }
    
    /**
     * Reads properties from eclipse-project file.
     * @param file
     * @return properties.
     * @throws IOException
     */
    private Properties fetchProperties(File file) throws IOException
    {
        final Properties properties = new Properties();
        try (final InputStream is = new FileInputStream(file))
        {
            properties.load(is);
        }
        return properties;
    }
    
    @SuppressWarnings("resource")
    @Override
    public Plugin loadPlugin(File file) throws InvalidPluginException, UnknownDependencyException
    {
        Validate.notNull(file, "File cannot be null"); //$NON-NLS-1$
        
        if (!(file.exists()))
        {
            throw new InvalidPluginException(new FileNotFoundException(file.getPath() + " does not exist")); //$NON-NLS-1$
        }
        PluginDescriptionFile description;
        try
        {
            description = getPluginDescription(file);
        }
        catch (InvalidDescriptionException ex)
        {
            throw new InvalidPluginException(ex);
        }
        
        File parentFile = file.getParentFile();
        File dataFolder = new File(parentFile, description.getName());
        
        File oldDataFolder = new File(parentFile, description.getRawName());
        
        if (!(dataFolder.equals(oldDataFolder)))
        {
            if ((dataFolder.isDirectory()) && (oldDataFolder.isDirectory()))
            {
                this.server.getLogger().warning(
                        String.format("While loading %s (%s) found old-data folder: `%s' next to the new one `%s'", new Object[] { description.getFullName(), file, oldDataFolder, dataFolder })); //$NON-NLS-1$
            }
            else if ((oldDataFolder.isDirectory()) && (!(dataFolder.exists())))
            {
                if (!(oldDataFolder.renameTo(dataFolder)))
                {
                    throw new InvalidPluginException("Unable to rename old data folder: `" + oldDataFolder + "' to: `" + dataFolder + "'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                this.server.getLogger().log(Level.INFO,
                        String.format("While loading %s (%s) renamed data folder: `%s' to `%s'", new Object[] { description.getFullName(), file, oldDataFolder, dataFolder })); //$NON-NLS-1$
            }
        }
        
        if ((dataFolder.exists()) && (!(dataFolder.isDirectory())))
        {
            throw new InvalidPluginException(String.format("Projected datafolder: `%s' for %s (%s) exists and is not a directory", new Object[] { dataFolder, description.getFullName(), file })); //$NON-NLS-1$
        }
        
        for (String pluginName : description.getDepend())
        {
            if (this.loaders == null)
            {
                throw new UnknownDependencyException(pluginName);
            }
            URLClassLoader current = this.loaders.get(pluginName);
            
            if (current == null)
            {
                throw new UnknownDependencyException(pluginName);
            }
        }
        URLClassLoader loader;
        Plugin plugin;
        try
        {
            final Class<? extends URLClassLoader> clazz = Class.forName("org.bukkit.plugin.java.PluginClassLoader").asSubclass(URLClassLoader.class); //$NON-NLS-1$
            final Constructor<? extends URLClassLoader> ctor = clazz.getDeclaredConstructor(JavaPluginLoader.class, ClassLoader.class, PluginDescriptionFile.class, File.class, File.class);
            final Properties props = fetchProperties(file);
            final File classesDir = new File(props.getProperty("classes")); //$NON-NLS-1$
            final URL[] additionalClasses = fetchAdditionalUrlsFromProperties(props);
            final ClassLoader appLoader = this.javaLoader.getClass().getClassLoader();
            if (additionalClasses != null && additionalClasses.length > 0)
            {
                final FakeClassLoader parentLoader = new FakeClassLoader(additionalClasses, appLoader, this.javaLoader);
                this.fakeLoaders.add(parentLoader);
                ctor.setAccessible(true);
                loader = ctor.newInstance(this.javaLoader, parentLoader, description, dataFolder, classesDir);
                parentLoader.injectPluginLoader(loader);
            }
            else
            {
                final FakeClassLoader parentLoader = new FakeClassLoader(new URL[0], appLoader, this.javaLoader);
                this.fakeLoaders.add(parentLoader);
                ctor.setAccessible(true);
                loader = ctor.newInstance(this.javaLoader, parentLoader, description, dataFolder, classesDir);
                parentLoader.injectPluginLoader(loader);
            }
            
            final Field field = clazz.getDeclaredField("plugin"); //$NON-NLS-1$
            field.setAccessible(true);
            plugin = (Plugin) field.get(loader);
        }
        catch (Throwable ex)
        {
            throw new InvalidPluginException(ex);
        }
        this.loaders.put(description.getName(), loader);
        
        return plugin;
    }
    
    /**
     * Fetched additional cloasses urls from properties
     * @param props
     * @return classes urls
     * @throws MalformedURLException
     */
    private URL[] fetchAdditionalUrlsFromProperties(Properties props) throws MalformedURLException
    {
        final List<URL> result = new ArrayList<>();
        if (props.containsKey("cpsize")) //$NON-NLS-1$
        {
            final int size = Integer.parseInt(props.getProperty("cpsize")); //$NON-NLS-1$
            for (int i = 0; i < size; i++)
            {
                final String type = props.getProperty("cptype" + i, "file"); //$NON-NLS-1$ //$NON-NLS-2$
                switch (type)
                {
                    case "file": //$NON-NLS-1$
                        result.add(new File(props.getProperty("cpfile" + i)).toURI().toURL()); //$NON-NLS-1$
                        break;
                    default:
                        // silently ignore
                        break;
                }
            }
        }
        return result.toArray(new URL[result.size()]);
    }
    
    @Override
    public PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException
    {
        Validate.notNull(file, "File cannot be null"); //$NON-NLS-1$
        
        try
        {
            final Properties props = fetchProperties(file);
            final File classesDir = new File(props.getProperty("classes")); //$NON-NLS-1$
            
            try (final InputStream is = new FileInputStream(new File(classesDir, "plugin.yml"))) //$NON-NLS-1$
            {
                return new PluginDescriptionFile(is);
            }
        }
        catch (IOException | YAMLException ex)
        {
            throw new InvalidDescriptionException(ex);
        }
    }
    
    private final class FakeClassLoader extends URLClassLoader
    {
        private URLClassLoader pluginLoader;
        
        private Map<String, Class<?>> classes = new ConcurrentHashMap();
        
        private final JavaPluginLoader loader;
        
        private final Method setClassMethod;

        private Method getClassByNameMethod;
        
        private Set<String> recursionCheck = new HashSet<>();

        /**
         * @param arg0
         * @param arg1
         * @throws SecurityException 
         * @throws NoSuchMethodException 
         */
        public FakeClassLoader(URL[] arg0, ClassLoader arg1, JavaPluginLoader loader) throws NoSuchMethodException, SecurityException
        {
            super(arg0, arg1);
            this.loader = loader;
            this.setClassMethod = this.loader.getClass().getDeclaredMethod("setClass", String.class, Class.class);
            this.setClassMethod.setAccessible(true);
            this.getClassByNameMethod = this.loader.getClass().getDeclaredMethod("getClassByName", String.class);
            this.getClassByNameMethod.setAccessible(true);
        }
        
        public void injectPluginLoader(URLClassLoader pluginLoader)
        {
            this.pluginLoader = pluginLoader;
            try
            {
                final Field classesField = pluginLoader.getClass().getDeclaredField("classes");
                classesField.setAccessible(true);
                final Map<String, Class<?>> classes = (Map<String, Class<?>>) classesField.get(pluginLoader);
                classes.putAll(this.classes);
                this.classes = classes;
            }
            catch (Exception ex)
            {
                throw new IllegalStateException(ex);
            }
        }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            return findClass(name, true);
        }

        Class<?> findClass(String name, boolean checkGlobal) throws ClassNotFoundException {
            if ((name.startsWith("org.bukkit.")) || (name.startsWith("net.minecraft."))) {
                throw new ClassNotFoundException(name);
            }
            if (debugcl) System.out.println("!CL" + this + "! FIND CLASS " + name);
            Class result = (Class) this.classes.get(name);
            if (debugcl) System.out.println("!CL" + this + "! RESULT " + result);

            if (result == null) {
                //boolean checkGlobal = cg && !this.recursionCheck.contains(name);
                if (checkGlobal) {
                    try
                    {
                        // this.recursionCheck.add(name);
                        if (debugcl) System.out.println("!CL" + this + "! INVOKE getClassByName ");
                        result = (Class<?>) this.getClassByNameMethod.invoke(this.loader, name);
                        if (debugcl) System.out.println("!CL" + this + "! getClassByName returns " + result);
                        if (result == null)
                        {
                            for (final FakeClassLoader fcl : fakeLoaders)
                            {
                                if (fcl == this) continue;
                                try
                                {
                                    if (debugcl) System.out.println("!CL" + this + "! INVOKE " + fcl);
                                    result = fcl.findClass(name, false);
                                    if (debugcl) System.out.println("!CL" + this + "! " + fcl + " returns " + result);
                                    break;
                                }
                                catch (ClassNotFoundException ex)
                                {
                                    // ignore
                                }
                            }
                        }
                    }
                    catch (@SuppressWarnings("unused") IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
                    {
                        // silently ignore
                    }
                    finally
                    {
                        this.recursionCheck.remove(name);
                    }
                }
                
                if (result == null)
                {
                    if (debugcl) System.out.println("!CL" + this + "! INVOKE super ");
                    result = super.findClass(name);
                    if (debugcl) System.out.println("!CL" + this + "! super returns " + result);
    
                    if (result != null) {
                        try
                        {
                            if (debugcl) System.out.println("!CL" + this + "! INVOKE setClass");
                            this.setClassMethod.invoke(this.loader, name, result);
                            if (debugcl) System.out.println("!CL" + this + "! FNISHED setClass");
                        }
                        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
                        {
                            throw new ClassNotFoundException("problems setting global class", e);
                        }
                    }
                }

                if (debugcl) System.out.println("!CL" + this + "! classes.put ");
                this.classes.put(name, result);
            }

            return result;
        }
        
    }
    
}
