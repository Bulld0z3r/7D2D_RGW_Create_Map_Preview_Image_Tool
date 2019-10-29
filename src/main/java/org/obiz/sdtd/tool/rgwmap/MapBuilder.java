/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package org.obiz.sdtd.tool.rgwmap;

import com.kitfox.svg.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static javafx.application.ConditionalFeature.SWT;
import static javax.swing.JFileChooser.DIRECTORIES_ONLY;

public class MapBuilder {

    private static final int MEM_EXPECTED = 512 * 1024 * 1024;
    private ConsoleWindow consoleWindow = null;
    private String path;
    private int downScale = 2; //2 - better definition
    private float gamma = 5;
    private final boolean DRAW_ICON_AXIS = false;
    private final int DRAW_ICON_SPRITE_BUF_SCALE = 2;

    //biome colors
    public static final Color forest = new Color(55, 95, 68);
    public static final Color snow = new Color(203, 197, 194);
    public static final Color desert = new Color(175, 154, 107);
    public static final Color wasteland = new Color(124, 116, 94);
    public static final Color burned = new Color(68, 70, 67);

    public static final int forestInt = ImageMath.getPureIntFromRGB(MapBuilder.forest);
    public static final int burnedInt = ImageMath.getPureIntFromRGB(MapBuilder.burned);
    public static final int desertInt = ImageMath.getPureIntFromRGB(MapBuilder.desert);
    public static final int snowInt = ImageMath.getPureIntFromRGB(MapBuilder.snow);
    public static final int wastelandInt = ImageMath.getPureIntFromRGB(MapBuilder.wasteland);


    //    private final  int MAP_IMAGE_TYPE = BufferedImage.TYPE_USHORT_555_RGB;
    private final  int MAP_IMAGE_TYPE = BufferedImage.TYPE_INT_RGB;
    private boolean applyGammaCorrection = true;
    private int mapSize;
    private int scaledSize;
    private long totalPixels;
    private BufferedImage iHeigths;
    private BufferedImage iBiomes;
    private BufferedImage iRad;
    private int waterLine;
    private boolean doBlureBiomes = true;
    private int bloorK = 256; //part of image size used as blure radius
    private Map<String, Path> icons;
    private static Map<String, BufferedImage> iconsCache = new HashMap<>();

    private int[][] bH;

    //fixed object sized (autoscaled)
    int i10 = 10 / (downScale);
    int i5 = i10 / 2;
    int i20 = 2 * i10;
    int i40 = 4 * i10;
    int i45 = (9 * i10) / 2;
    int i160 = 16 * i10;
    int i200 = 20 * i10;
    int i250 = 25 * i10;

    int fileNum = 1;
    private BufferedImage iWaterZones;
    
    private long prevLogTime;
    private String lastFileName;
    private Color ROAD_MAIN_COLOR = new Color(141, 129, 106);;
    private Color ROAD_SECONDARY_COLOR = new Color(52, 59, 65);

    public MapBuilder(String path) {
        this.path = path;
        prevLogTime = System.currentTimeMillis();
        try {
            icons = loadIcons();
            consoleWindow = new ConsoleWindow();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        //TODO command line options
        String path = ".";
        if(args.length==1) {
            path = args[0];
        }

        //get runtime memory information
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        System.out.println("totalMemory = " + totalMemory);
        System.out.println("freeMemory = " + freeMemory);
        System.out.println("maxMemory = " + maxMemory);

        if(maxMemory < MEM_EXPECTED) {
            System.out.println("TOO LITTLE");
            JOptionPane.showMessageDialog(null, "There is too little mem for me :(\nI'm trying to restart my self for grab much mem!","Not enough mem error", JOptionPane.ERROR_MESSAGE);
            String jarName = new File(MapBuilder.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath())
                    .getAbsolutePath();

            System.out.println("jarName = " + jarName);
            if(jarName.endsWith("jar")) {
                System.out.println("Do the magic!");

                try {
                    // re-launch the app itselft with VM option passed
                    Process p;
                    if(args.length>0) {
                        System.out.println("With args");
                        p = Runtime.getRuntime().exec(new String[]{"java", "-Xmx1024m", "-jar", jarName, args[0]});
                    } else {
                        System.out.println("Without args");
                        p = Runtime.getRuntime().exec(new String[]{"java", "-Xmx1024m", "-jar", jarName});
                    }
                    Thread.sleep(10);
                    System.exit(0);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                //just die if it's running from IDE
                System.exit(-1);
            }
        } else {
            System.out.println("Enough mem! Let's work!");
        }

        new MapBuilder(path).build();
    }

    public static void clearIconsCache() {
        iconsCache.clear();
    }

    private void build() {
        try {
            Timer.startTimer("OverAll");
//            testShowMap();
//            if(true) return;
            //testGetSprite("bank");
            readWorldHeights();
//            testWalkHeigths();
            readWatersPoint();
            autoAjustImage();
            loadBiomes();
            applyHeightsToBiomes();
            drawRoads();
            drawPrefabs();
            log(    "\n------------------- All work done! ------------------- \n\n" +
                    "          Result map image: " + lastFileName + "\n\n" +
                    "------------------------------------------------------");
            Timer.stopTimer("OverAll");

            new PreviewFrame(iBiomes, icons).setVisible(true);

        } catch (IOException e) {

            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }

    private void testShowMap() {
        try {
            BufferedImage map = ImageIO.read(new File("8_mapWithObjects.png"));
            new PreviewFrame(map, icons).setVisible(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void testWalkHeigths() {
        log("Start search max H in BufferedImage");
        //[+0.319s]
        WritableRaster iHeigthsRaster = iHeigths.getRaster();
        int maxH = iHeigthsRaster.getSample(0, 0, 0);
        for (int x = 0; x < scaledSize; x++) {
            for (int y = 0; y < scaledSize; y++) {
                int h = iHeigthsRaster.getSample(x, y, 0);
                if(h>maxH) {
                    maxH = h;
                }
            }
        }
        log("Finish search max H in BufferedImage: " + maxH);
        log("Start search max H in BufferedImage2");
        maxH = iHeigths.getRGB(0, 0);
        //[+1.555s]
        for (int x = 0; x < scaledSize; x++) {
            for (int y = 0; y < scaledSize; y++) {
                int h = iHeigths.getRGB(x, y);
                if(h>maxH) {
                    maxH = h;
                }
            }
        }
        log("Finish search max H in BufferedImage2: " + maxH);
        log("Start search max H in int[][]");
        maxH = bH[0][0];
        //[+0.018s]
        for (int i = 0; i < scaledSize; i++) {
            for (int j = 0; j < scaledSize; j++) {
                int h = bH[i][j];
                if(h>maxH) {
                    maxH = h;
                }
            }
        }
        log("Finish search max H in int[][]: " + maxH);
    }

    private void testGetSprite(String iconName) {
        BufferedImage map = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gMap = map.createGraphics();
        gMap.setColor(Color.GRAY);
        gMap.drawRect(0, 0, 1023, 1023);
        for (int i = 0; i < 1024; i+=32) {
            gMap.drawLine(0, i,1023, i);
            gMap.drawLine(i,0, i, 1023);
        }
        int iconSize = 64;
        int x = 512, y = 512;

        try {
            ImageIO.write(map, "PNG", new File("_tst_map.png"));

            drawIcon(gMap, iconName, iconSize, x, y, true, icons, DRAW_ICON_SPRITE_BUF_SCALE, false);

            ImageIO.write(map, "PNG", new File("_tst_map2.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void drawIcon(Graphics gMap, String iconName, int targetSize, int x, int y, boolean showAxis, Map<String, Path> icons, int sizeBufferScale, boolean ignoreScale) {
        drawIcon(gMap, iconName, targetSize, x, y, showAxis, icons, sizeBufferScale, iconsCache, ignoreScale);
    }
    public static void drawIcon(Graphics gMap, String iconName, int targetSize, int x, int y, boolean showAxis, Map<String, Path> icons, int sizeBufferScale, Map<String, BufferedImage> iconsCache, boolean ignoreScale) {
        BufferedImage sprite;
        sprite = iconsCache.get(iconName);
        if(sprite == null) {
            sprite = createSprite(iconName, targetSize, showAxis, icons, sizeBufferScale, ignoreScale);
            iconsCache.put(iconName, sprite);
        }
        gMap.drawImage(sprite, x - targetSize * sizeBufferScale, y - targetSize * sizeBufferScale, null);
    }

    public static BufferedImage createSprite(String name, int width, boolean showAxis, Map<String, Path> icons, int halfSize, boolean ignoreScale) {
        int fullSize = halfSize*2;
        BufferedImage img = new BufferedImage(width* fullSize, width* fullSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            Path path = icons.get(name);
            SVGUniverse svgUniverse = new SVGUniverse();

            URI uri = svgUniverse.loadSVG(Files.newInputStream(path), path.getFileName().toString());
            SVGDiagram diagram = svgUniverse.getDiagram(uri);
            int svgWidth =  width;
            int svgX = 0;
            int svgY = 0;
            if(ignoreScale) {
                svgWidth = Math.round(width / (diagram.getRoot().getDeviceHeight() / 100));
                AffineTransform xForm = ((Group) diagram.getRoot().getChild(0)).getXForm();
                if(xForm!=null) {
                    int svgViewBoxWidth = diagram.getRoot().getPresAbsolute("viewBox").getIntList()[3];
                    double scale = svgWidth/(svgViewBoxWidth*1.);
                    svgX = (int) -(xForm.getTranslateX() * scale);
                    svgY = (int) -(xForm.getTranslateY() * scale);
                }
            }
            diagram.setDeviceViewport(new Rectangle(svgWidth, svgWidth));
//            diagram.
            Graphics graphics = g.create(svgX, svgY, width* fullSize, width* fullSize);
            if(showAxis) {
                g.setColor(Color.GREEN);
                g.drawLine(0, width* halfSize, width* fullSize -1, width* halfSize);
                g.drawLine(width* halfSize,0, width* halfSize, width* fullSize -1);
            }
            graphics.translate(width* halfSize, width* halfSize);
            diagram.render((Graphics2D) graphics);
            svgUniverse.clear();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SVGException e) {
            e.printStackTrace();
        }
        return img;
    }

    public static Map<String, Path> loadIcons() throws IOException, URISyntaxException {
        Map<String, Path> result = new HashMap<>();
        String resourceName = "/icons";
        Path myPath = getPathForResource(resourceName);
        Stream<Path> walk = Files.walk(myPath, 1);

        walk.forEach(
                next -> {
                    String nextFile = next.getFileName().toString();
                    if(Files.isRegularFile(next) && nextFile.endsWith(".svg")) {
                        nextFile = nextFile.substring(0, nextFile.lastIndexOf("."));
                        result.put(nextFile, next);
                    }
                }
        );
        return result;
    }

    public static Path getPathForResource(String resourceName) throws URISyntaxException, IOException {
        Path myPath;
        URI uri = MapBuilder.class.getResource(resourceName).toURI();
        if (uri.getScheme().equals("jar")) {
            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
            myPath = fileSystem.getPath(resourceName);
        } else {
            myPath = Paths.get(uri);
        }
        return myPath;
    }

    private void readWatersPoint() throws IOException, XMLStreamException {
        log("Load WaterZones.");
        String prefabs = "\\water_info.xml";
        XMLInputFactory xmlif = XMLInputFactory.newInstance();
        XMLStreamReader xmlr = xmlif.createXMLStreamReader(prefabs, new FileInputStream(path + prefabs));

        int watersPointsCounter = 0;

        iWaterZones = new BufferedImage(scaledSize, scaledSize, BufferedImage.TYPE_BYTE_BINARY);

        Graphics graphics = iWaterZones.getGraphics();

        int eventType;
        while (xmlr.hasNext()) {
            eventType = xmlr.next();
            if (eventType == XMLEvent.START_ELEMENT) {
                if (xmlr.getAttributeCount() == 5) {
                    String attributeValue = xmlr.getAttributeValue(0);
                    String[] split = attributeValue.split(",");
                    int x = (mapSize / 2 + Integer.parseInt(split[0].trim())) / downScale;
                    int y = (mapSize / 2 - Integer.parseInt(split[2].trim())) / downScale;

                    graphics.setColor(Color.WHITE);
                    graphics.fillRect((int) (x - i250 / downScale * 0.75), (int) (y - i250 / downScale * 1.25), i160, i200);

                    watersPointsCounter++;
                }
            }
        }

        log(watersPointsCounter + " water sources loaded.");
        writeToFile("_waterZones", iWaterZones);
    }

    private void drawPrefabs() throws IOException, XMLStreamException {
        String prefabs = "\\prefabs.xml";
        XMLInputFactory xmlif = XMLInputFactory.newInstance();
        XMLStreamReader xmlr = xmlif.createXMLStreamReader(prefabs, new FileInputStream(path + prefabs));

        Graphics2D g = iBiomes.createGraphics();

        int eventType;

        Set<String> prefabsGroups = icons.keySet();
        int prefabsSVGCounter = 0;
        int prefabsCounter = 0;

        Timer.startTimer("Draw prefabs");
        log("Processing prefabs: ");

        while (xmlr.hasNext()) {
            eventType = xmlr.next();
            if (eventType == XMLEvent.START_ELEMENT) {
                if (xmlr.getAttributeCount() == 4) {
                    String attributeValue = xmlr.getAttributeValue(2);
                    String[] split = attributeValue.split(",");
                    int x = (mapSize / 2 + Integer.parseInt(split[0])) / downScale;
                    int y = (mapSize / 2 - Integer.parseInt(split[2])) / downScale;

                    int rot = Integer.parseInt(xmlr.getAttributeValue(3));
                    int xShift = x + i10;
                    int yShift = y - i45;

                    String prefabName = xmlr.getAttributeValue(1);
                    String foundPrefabGroup = null;

                    loopPrefabsGroups:
                    for (String prefabsGroup : prefabsGroups) {
                        if(prefabName.contains(prefabsGroup)) {
                            foundPrefabGroup = prefabsGroup;
                            prefabsSVGCounter++;
                            break loopPrefabsGroups;
                        }
                    }

                    prefabsCounter++;

                    if (foundPrefabGroup != null) {
                        drawIcon(g, foundPrefabGroup, i40, xShift, yShift, DRAW_ICON_AXIS, icons, DRAW_ICON_SPRITE_BUF_SCALE, false);
                    } else if (prefabName.contains("trailer")) {
                        g.setColor(new Color(51, 49, 51));
                        if (rot == 0 || rot == 2)
                            g.fill3DRect(x + i5, yShift + i20, i10, i20, true);
                        else
                            g.fill3DRect(x + i5, yShift + i20, i20, i10, true);
                    } else if (prefabName.contains("sign")) {
                        g.setColor(new Color(51, 49, 51));
                        g.fill3DRect(x, y, i10, i10, true);
                    } else {
                        drawIcon(g, "NA", i40, x, y, DRAW_ICON_AXIS, icons, DRAW_ICON_SPRITE_BUF_SCALE, false);
                    }
                }
            }
        }


        log( prefabsCounter + " prefabs added, " + prefabsSVGCounter + " of them added from SVG.");
        Timer.stopTimer("Draw prefabs");
        log("Start write finish image.");
        writeToFile("_mapWithObjects", iBiomes, false);
        log("Finish write finish image.");
    }

    private void drawRoads() throws IOException {
        log("Load roads file");
        BufferedImage roads = ImageIO.read(new File(path + "\\splat3.png"));
        log("Roads loaded. Start drawing.");

//        Color roadColor;

        DataBuffer db = iBiomes.getRaster().getDataBuffer();

        DataBuffer rdb = roads.getAlphaRaster().getDataBuffer();
        boolean firstTime = true;

        System.out.println("TEST : " + (rdb.getSize()/4-mapSize*mapSize));

        //TODO multithread

        for (int i = 0; i < scaledSize; i++) {
            for (int j = 0; j < scaledSize; j++) {
                int c2 = rdb.getElem(ImageMath.xy2i(roads,i*downScale, j*downScale, 2));
                if(c2!=0) {
//                    db.setElem(ImageMath.xy2i(iBiomes, i, j), ImageMath.getPureIntFromRGB(255, 201, 14));
                    db.setElem(ImageMath.xy2i(iBiomes, i, j), ImageMath.getPureIntFromRGB(ROAD_MAIN_COLOR));
                }
                int c3 = rdb.getElem(ImageMath.xy2i(roads,i*downScale, j*downScale, 3));
                if(c3!=0) {
//                    db.setElem(ImageMath.xy2i(iBiomes, i, j), ImageMath.getPureIntFromRGB(67, 163, 203));
                    db.setElem(ImageMath.xy2i(iBiomes, i, j), ImageMath.getPureIntFromRGB(ROAD_SECONDARY_COLOR));
                }
            }
        }
        log("Finish roads drawing.");

        writeToFile("_map_with_roads", iBiomes);
    }

    private void applyHeightsToBiomes() throws IOException {
        long start, end;

        //mark radiation zones
        drawRadiation();

        log("Start bluring biomes.");
        if (doBlureBiomes) {
            BufferedImage iBiomesBlured = new BufferedImage(scaledSize, scaledSize, MAP_IMAGE_TYPE);
            new BoxBlurFilter(scaledSize / bloorK, scaledSize / bloorK, 1).filter(iBiomes, iBiomesBlured);
            iBiomes.flush();
            iBiomes = iBiomesBlured;
        }
        log("Finish bluring biomes. Start drawing lakes.");

        //Draw lakes
        //TODO no need to  walk through whole image. Save water points and use it to walk around +/- water spot square
        WritableRaster iHeigthsRaster = iHeigths.getRaster();
        for (int x = 0; x < scaledSize; x++) {
            for (int y = 0; y < scaledSize; y++) {
                if (iHeigthsRaster.getSample(x, y, 0) < waterLine
                        && iWaterZones.getRaster().getSample(x, y, 0) > 0) {
                    iBiomes.setRGB(x, y, new Color(49, 87, 145).getRGB());
                }
            }
        }

        log("Finish drawing lakes.");

        start = System.nanoTime();
        writeToFile("_bump", iHeigths);
        writeToFile("_biomes", iBiomes);
        end = System.nanoTime();
        log("File saving time:  = " + (end - start) / 1000000000 + "s");

        // normal vectors array
        log("Start alloc normal vectors array");
        float[] normalVectorsX = new float[scaledSize * scaledSize];
        float[] normalVectorsY = new float[scaledSize * scaledSize];
        float[] normalVectorsZ = new float[scaledSize * scaledSize];
        log("Finish alloc normal vectors array");
        // precalculate normal vectors
        BumpMappingUtils.FindNormalVectors(iHeigths, normalVectorsX, normalVectorsY, normalVectorsZ);
        log("Normal vectors are saved.");
        //free mem
        iHeigths.flush();
        //apply bump-mapping using normal vectors
        BumpMappingUtils.paint(iBiomes, scaledSize, scaledSize, normalVectorsX, normalVectorsY, normalVectorsZ);
        log("Bump mapping applied.");
        //Write bump-mapped biomes
        writeToFile("_biomesShadow", iBiomes);
    }

    private BufferedImage loadBiomes() throws IOException {
        log("start load biomes.png");
        BufferedImage inputImage = ImageIO.read(new File(path + "\\biomes.png"));
        log("Finish load biomes.png. Start scaling.");

        iBiomes = new BufferedImage(scaledSize, scaledSize, MAP_IMAGE_TYPE);

        // scale the input biomes image to the output image size
        Graphics2D g2d = iBiomes.createGraphics();
        g2d.drawImage(inputImage, 0, 0, scaledSize, scaledSize, null);
        g2d.dispose();

        //free mem
        inputImage.flush();

        log("Finish scaling. Start color mapping.");

        DataBuffer dataBuffer = iBiomes.getRaster().getDataBuffer();

        int dataBufferSize = dataBuffer.getSize();
        System.out.println("dataBufferSize = " + dataBufferSize);
        for (int i = 0; i < dataBufferSize; i++) {
            dataBuffer.setElem(i, mapBiomeRasterColor(dataBuffer.getElem(i)));
        }

        log("Finish color mapping.");
        writeToFile("_recolorBiomes", iBiomes);
        log("File written.");
        return inputImage;
    }

    private int mapBiomeRasterColor(int rgb) {
        switch (rgb) {
            case 16777215:
                return snowInt; //snow
            case 16770167:
                return desertInt;  //desert
            case 16384:
                return forestInt; //forest
            case 16754688:
                return wastelandInt; //wasteland
            case 12189951:
                return burnedInt; //burned
        }
        return rgb;
    }

    private int mapBiomeColor(int rgb) {
        switch (rgb) {
            case -16760832:
                return forest.getRGB();
            case -1:
                return snow.getRGB();
            case -7049:
                return desert.getRGB();
            case -22528:
                return wasteland.getRGB();
            case -4587265:
                return burned.getRGB();
        }
        return rgb;
    }


    private void writeToFile(String fileName, BufferedImage imgToSave) throws IOException {
        writeToFile(fileName, imgToSave, true);
    }
    private void writeToFile(String fileName, BufferedImage imgToSave, boolean checkExists) throws IOException {
        fileNum++;
        if (!checkExists || !checkFileExists(fileName)) {
            lastFileName = fileNum + fileName + ".png";
            File biomesShadow = new File(path + "\\" + lastFileName);
            ImageIO.write(imgToSave, "PNG", biomesShadow);
        } else {
//            fileNum--;
        }

    }

    private void autoAjustImage() throws IOException {
        log("Start autoAjustImage");
        WritableRaster raster = iHeigths.getRaster();
        // initialisation of image histogram array
        long hist[] = new long[256];
        for (int i = 0; i < hist.length; i++) {
            hist[i] = 0;
        }
        //time measurement vars
        long start, end;
        //init other stats
        int min = raster.getSample(raster.getMinX(), raster.getMinY(), 0);
        int max = min;
        long rms = 0;
        double mean = 0;
        int tcount = 0;

        start = System.nanoTime();
        //TODO multithread
        for (int x = 0; x < scaledSize; x++) {
            for (int y = 0; y < scaledSize; y++) {

                //get integer height value from a current pixel
                int color = raster.getSample(x, y, 0);

                //find min and max heights
                if (color < min) {
                    min = color;
                } else if (color > max) {
                    max = color;
                }

                //build histogram
                hist[color / 256]++;

                //calulate MEAN
                mean += color * 1. / totalPixels;
                long lColor = (long) color;
                lColor *= lColor;
                lColor /= totalPixels;
                rms += lColor;

                //just check pixels count
                tcount++;
            }
        }
        assert tcount == totalPixels;
        end = System.nanoTime();
        long t1 = end - start;
        log("Time to solve stats: " + t1 / 1000000 + "ms");

        rms = Math.round(Math.sqrt(rms));
        int intrms = Math.toIntExact(rms);

//        log("mean = " + Math.round(mean));
//        log("rms = " + rms);
//        log("min = " + min);
//        log("max = " + max);

        StringBuilder sb = new StringBuilder();
        float D = 0;
        for (int i = 0; i < hist.length; i++) {
            sb.append(i * 256 + "\t").append(hist[i]).append('\n');
            long a = i * 256 - rms;
            double tmp = Math.pow(a, 2);
            tmp /= tcount;
            tmp *= hist[i];
            D += tmp;
        }

        Files.write(Paths.get(path + "\\heigthsHistogram.txt"), Collections.singleton(sb));

        D = Math.round(Math.sqrt(D));
        float k = 256 * 256 / (max - min);
        log("k = " + k);

        waterLine = intrms - Math.round(1.7f * D);
        log("waterLine = " + waterLine);
        if (applyGammaCorrection) {
            waterLine = Math.round((waterLine - min) * k);
            log("after gamma waterLine = " + waterLine);
            log("Start apply gamma correction.");
            //TODO multithread
            for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
                    int grayColor = raster.getSample(x, y, 0);
                    int imageColor = Math.round((grayColor - min) * k);
                    raster.setSample(x, y, 0, imageColor);
                }
            }
            log("End apply gamma correction.");
        }
    }

    public void readWorldHeights() throws IOException {
        File heightsFile;

            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new java.io.File(System.getenv("USERPROFILE")+"\\AppData\\Roaming\\7DaysToDie\\GeneratedWorlds"));
            chooser.setDialogTitle("Choose world..");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setPreferredSize(new Dimension(860, 550));
            while (true) {
                String dtmFileName = path + "\\dtm.raw";
                heightsFile = new File(dtmFileName);
                if (!heightsFile.exists() || !heightsFile.isFile() || !heightsFile.canRead()) {
                    System.err.println("File not found: " + dtmFileName);
                    if (chooser.showOpenDialog(consoleWindow) == JFileChooser.APPROVE_OPTION) {
                        path = chooser.getSelectedFile().getAbsolutePath();
                    } else {
                        System.out.println("No Selection ");
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        System.exit(1);
                    }
                } else {
                    break;
                }
            }


        long fileLength = heightsFile.length();
        log("dtm.raw fileLength = " + fileLength);
        mapSize = (int) Math.round(Math.sqrt(fileLength / 2.));
        log("Detected mapSize: " + mapSize);
        scaledSize = mapSize / downScale;
        log("Resulting image side size will be: " + scaledSize + "px");
        //TODO rename to totalScaledPixels
        totalPixels = scaledSize;
        totalPixels *= totalPixels;
        //Result processed heights image
        iHeigths = new BufferedImage(scaledSize, scaledSize, BufferedImage.TYPE_USHORT_GRAY);
        WritableRaster raster = iHeigths.getRaster();

        bH = new int[scaledSize][scaledSize];

        try (FileInputStream hmis = new FileInputStream(heightsFile)) {
            byte buf[] = new byte[mapSize * 4];

            int readedBytes;
            int curPixelNum = 0;
            Set<Integer> grayColors = new HashSet<>();
            System.out.print("File load:\n|----------------|\n|");
            while ((readedBytes = hmis.read(buf)) > -1) {
                //TODO here potential problem if readedBytes%2 != 0
                //convert every 2 bytes to new gray pixel
                for (int i = 0; i < readedBytes / 2; i++) {
                    //TODO use avg of pixel color with same coordinate in scaled image.
                    //calculate pixel position
                    int x = (curPixelNum % mapSize) / downScale;
                    int y = (mapSize - 1 - curPixelNum / mapSize) / downScale;
                    //write pixel to resulting image
                    int grayColor = (buf[i * 2 + 1] << 8) | (((int) buf[i * 2]) & 0xff);
                    if(!grayColors.contains(grayColor)){
                        grayColors.add(grayColor);
                    }
                    raster.setSample(x, y, 0, grayColor);
//                    int sample = raster.getSample(x, y, 0);
//                    if(sample !=grayColor ) {
//                        log("PROBLEM: sample="+sample+" grayColor="+grayColor);
//                    }
                    bH[x][y] = grayColor;
                    curPixelNum++;
                    //Draw progress bar
                    if (curPixelNum % (mapSize * 512) == 0) {
                        System.out.print("-");
                    }
                }
            }
            log("|\nFinish load dtm.raw. Colors count:" + grayColors.size());
        }
    }

    private void drawRadiation() throws IOException {


        log("Load radiation map..");
        BufferedImage inputImage = ImageIO.read(new File(path + "\\radiation.png"));
        log("Beware of radiation!");

        iRad = new BufferedImage(scaledSize, scaledSize, MAP_IMAGE_TYPE);

        // scale the input radiation zone image to the output image size
        log("Start scale radiation..");
        Graphics2D g2d = iRad.createGraphics();
        g2d.drawImage(inputImage, 0, 0, scaledSize, scaledSize, null);
        g2d.dispose();

        //free mem
        inputImage.flush();
        log("Start draw radiation.");
        //TODO multithread
        DataBuffer biomesDB = iBiomes.getRaster().getDataBuffer();
        DataBuffer radiationDB = iRad.getRaster().getDataBuffer();
        for (int i = 0; i < biomesDB.getSize(); i++) {
            int rgb = ImageMath.getFillIntFromPureInt(biomesDB.getElem(i));
            int rgbRad = ImageMath.getFillIntFromPureInt(radiationDB.getElem(i));
            if (rgbRad == -65536) {
                int oldR = rgb >> 16 & 0xff;
                int oldG = rgb >> 8 & 0xff;
                int oldB = rgb & 0xff;

                int newR = (int) (oldR * 1.5);
                if (newR > 255) newR = 255;
                if (newR < 0) newR = 0;

                biomesDB.setElem(i, ImageMath.getPureIntFromRGB(newR, oldG, oldB));
//                iBiomes.setRGB(x, y, new Color(newR, oldG, oldB).getRGB());
            }

        }
        log("End draw radiation.");

    }

    public boolean checkFileExists(String fileName) {
        String filePath = path + "\\" + fileNum + fileName + ".png";
        File f = new File(filePath);
        if (!f.exists() || !f.isFile() || !f.canRead()) {
            return false;
        } else {
            log("File already exists: " + fileNum + fileName + ".png");
        }

        return true;
    }

    private void log(String message) {
        long now = System.currentTimeMillis();
        System.out.println("[+" + (now-prevLogTime)/1000f + "s]: " + message);
        prevLogTime = now;
    }

}
