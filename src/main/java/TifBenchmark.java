import fiji.PerformanceProfiler;
import ij.ImagePlus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.io.ImgIOException;
import net.imglib2.io.ImgOpener;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.BenchmarkHelper;


public class TifBenchmark
{
	public static Img< FloatType > loadSlicesImgOpener( final String[] sliceFilenames ) throws ImgIOException
	{
		final int numSlices = sliceFilenames.length;
		final ArrayImgFactory< FloatType > factory = new ArrayImgFactory< FloatType >();
		final FloatType type = new FloatType();
		final ImgOpener o = new ImgOpener();
		o.setGroupFiles(true);

		Img< FloatType > slice = null;
		for ( int z = 0; z < numSlices; ++z )
		{
			slice = o.openImg( sliceFilenames[ z ], factory, type );
		}

		return slice;
	}

	public static Img< FloatType > loadSlicesImgOpenerPlanar( final String[] sliceFilenames ) throws ImgIOException
	{
		final int numSlices = sliceFilenames.length;
		final PlanarImgFactory<FloatType> factory = new PlanarImgFactory< FloatType >();
		final FloatType type = new FloatType();
		final ImgOpener o = new ImgOpener();
		o.setGroupFiles(true);

		Img< FloatType > slice = null;
		for ( int z = 0; z < numSlices; ++z )
		{
			slice = o.openImg( sliceFilenames[ z ], factory, type );
		}

		return slice;
	}

	public static Img< FloatType > loadSlicesImgOpenerPlanarNoGroupedFiles( final String[] sliceFilenames ) throws ImgIOException
	{
		final int numSlices = sliceFilenames.length;
		final PlanarImgFactory<FloatType> factory = new PlanarImgFactory< FloatType >();
		final FloatType type = new FloatType();
		final ImgOpener o = new ImgOpener();
		o.setGroupFiles(false);
		o.setGetReaderSlow(false);

		Img< FloatType > slice = null;
		for ( int z = 0; z < numSlices; ++z )
		{
			slice = o.openImg( sliceFilenames[ z ], factory, type );
		}

		return slice;
	}

	public static Img< FloatType > loadSlicesImageJ( final String[] sliceFilenames )
	{
		final int numSlices = sliceFilenames.length;

		Img< FloatType > slice = null;
		for ( int z = 0; z < numSlices; ++z )
		{
			slice = ImageJFunctions.wrapFloat( new ImagePlus( sliceFilenames[ z ] ) );
		}

		return slice;
	}

	private static void copyFile( final File sourceFile, final File destFile ) throws IOException
	{
		if ( !sourceFile.exists() )
			return;
		if ( !destFile.exists() )
			destFile.createNewFile();
		final FileInputStream sourceStream = new FileInputStream( sourceFile );
		final FileChannel source = sourceStream.getChannel();
		final FileOutputStream destStream = new FileOutputStream( destFile );
		final FileChannel destination = destStream.getChannel();
		if ( destination != null && source != null )
		{
			destination.transferFrom( source, 0, source.size() );
		}
		if ( source != null ) {
			source.close();
			sourceStream.close();
		}
		if ( destination != null ) {
			destination.close();
			destStream.close();
		}
	}

	public static boolean profile = true;

	public static void main( final String... args ) throws Throwable
	{
		// start the profiler (relaunches the class in an instrumenting class loader)
		if (profile && PerformanceProfiler.startProfiling(null, args)) return;

		final int numRuns = 5; // how many times the benchmark is run
		final int numSlices = 50; // how many slices to load per benchmark
		final int numDummyFiles = 10000; // how many empty .tif files to create in the same directory as the image file

		final File tmpDir = File.createTempFile( "images", null );
		tmpDir.delete();
		tmpDir.mkdir();
		for ( int i = 0; i < numDummyFiles; ++i )
		{
			final File dummy = new File( tmpDir + "/" + i + ".tif" );
			dummy.createNewFile();
		}
		final String filename = tmpDir + "/slice.tif";
		copyFile( new File( TifBenchmark.class.getResource( "slice.tif" ).getFile() ), new File( filename ) );

		final String[] sliceFilenames = new String[ numSlices ];
		for ( int i = 0; i < sliceFilenames.length; ++i )
			sliceFilenames[ i ] = filename;

		boolean runIJ1 = false;
		boolean skipTooSlow = true;

		// reset profiling counters
		if (profile) PerformanceProfiler.report(null);
		if (runIJ1) {
			System.out.println( "loading " + numSlices + " tif images using ImageJ, " + numDummyFiles + " other tif files in same directory" );
			// restart profiling
			if (profile) PerformanceProfiler.setActive(true);
			BenchmarkHelper.benchmarkAndPrint( numRuns, false, new Runnable()
			{
				@Override
				public void run()
				{
					loadSlicesImageJ( sliceFilenames );
				}
			} );
			// report
			if (profile) PerformanceProfiler.report(new File("/tmp", "ij.log.out"), 3);
		}

		/*
		 * Ask SCIFIO nicely to stop copying stuff around and to use mapped
		 * files, thankyouverymuch.
		 */
		System.setProperty("mappedBuffers", "true");

		// let's not let instrumentation dominate the performance statistics
		if (profile) loadSlicesImgOpenerPlanarNoGroupedFiles( sliceFilenames );

		System.out.println( "loading " + numSlices + " tif images using ImgOpener (Planar, disallow file grouping), " + numDummyFiles + " other tif files in same directory" );
		if (profile) PerformanceProfiler.setActive(true);
		BenchmarkHelper.benchmarkAndPrint( numRuns, false, new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					loadSlicesImgOpenerPlanarNoGroupedFiles( sliceFilenames );
				}
				catch ( final ImgIOException e )
				{
					e.printStackTrace();
				}
			}
		} );
		if (profile) PerformanceProfiler.report(new File("/tmp", "img-ungrouped-planar.log.out"), 3);

		if (skipTooSlow) return; // the remaining cases are slower, only kept for reference.

		System.out.println( "loading " + numSlices + " tif images using ImgOpener (Planar), " + numDummyFiles + " other tif files in same directory" );
		if (profile) PerformanceProfiler.setActive(true);
		BenchmarkHelper.benchmarkAndPrint( numRuns, false, new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					loadSlicesImgOpenerPlanar( sliceFilenames );
				}
				catch ( final ImgIOException e )
				{
					e.printStackTrace();
				}
			}
		} );
		if (profile) PerformanceProfiler.report(new File("/tmp", "img-planar.log.out"), 3);

		System.out.println( "loading " + numSlices + " tif images using ImgOpener, " + numDummyFiles + " other tif files in same directory" );
		if (profile) PerformanceProfiler.setActive(true);
		BenchmarkHelper.benchmarkAndPrint( numRuns, false, new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					loadSlicesImgOpener( sliceFilenames );
				}
				catch ( final ImgIOException e )
				{
					e.printStackTrace();
				}
			}
		} );
		if (profile) PerformanceProfiler.report(new File("/tmp", "img-array.log.out"), 3);

		for ( int i = 0; i < numDummyFiles; ++i )
		{
			final File dummy = new File( tmpDir + "/" + i + ".tif" );
			dummy.delete();
		}
		new File( filename ).delete();
		tmpDir.delete();
	}
}
