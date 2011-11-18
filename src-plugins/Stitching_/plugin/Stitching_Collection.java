package plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import stitching.ImageInformation;

import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.util.Util;
import mpicbg.models.InvertibleBoundable;
import mpicbg.stitching.ComparePair;
import mpicbg.stitching.GlobalOptimization;
import mpicbg.stitching.ImagePlusTimePoint;
import mpicbg.stitching.PairWiseStitchingImgLib;
import mpicbg.stitching.PairWiseStitchingResult;
import mpicbg.stitching.StitchingParameters;

public class Stitching_Collection 
{

	public static void stitchCollection( final ArrayList< ImageCollectionElement > elements, final StitchingParameters params )
	{
		// find overlappint tiles
		final Vector< ComparePair > pairs = findOverlappingTiles( elements, params );
		
		// compute all compare pairs
		// compute all matchings
		final AtomicInteger ai = new AtomicInteger(0);
		
		final int numThreads;
		
		if ( params.cpuMemChoice == 0 )
			numThreads = 1;
		else
			numThreads = Runtime.getRuntime().availableProcessors();
		
        final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );
    	
        for ( int ithread = 0; ithread < threads.length; ++ithread )
            threads[ ithread ] = new Thread(new Runnable()
            {
                public void run()
                {		
                   	final int myNumber = ai.getAndIncrement();
                    
                    for ( int i = 0; i < pairs.size(); i++ )
                    {
                    	if ( i % numThreads == myNumber )
                    	{
                    		final ComparePair pair = pairs.get( i );
                    		
                    		long start = System.currentTimeMillis();			
                			
                    		// where do we approximately overlap?
                			final Roi roi1 = getROI( pair.getTile1().getElement(), pair.getTile2().getElement() );
                			final Roi roi2 = getROI( pair.getTile2().getElement(), pair.getTile1().getElement() );
                			
                			pair.getImagePlus1().setRoi( roi1 );
                			pair.getImagePlus2().setRoi( roi2 );
                			
            				final PairWiseStitchingResult result = PairWiseStitchingImgLib.stitchPairwise( pair.getImagePlus1(), pair.getImagePlus2(), roi1, roi2, pair.getTimePoint1(), pair.getTimePoint2(), params );			

            				if ( params.dimensionality == 2 )
            					pair.setRelativeShift( new float[]{ result.getOffset( 0 ), result.getOffset( 1 ) } );
            				else
            					pair.setRelativeShift( new float[]{ result.getOffset( 0 ), result.getOffset( 1 ), result.getOffset( 2 ) } );
            				
            				pair.setCrossCorrelation( result.getCrossCorrelation() );

            				IJ.log( pair.getImagePlus1().getTitle() + "[" + pair.getTimePoint1() + "]" + " <- " + pair.getImagePlus2().getTitle() + "[" + pair.getTimePoint2() + "]" + ": " + 
            						Util.printCoordinates( result.getOffset() ) + " correlation (R)=" + result.getCrossCorrelation() + " (" + (System.currentTimeMillis() - start) + " ms)");
                    	}
                    }
                }
            });
        
        SimpleMultiThreading.startAndJoin( threads );
		
        // get the final positions of all tiles
		final ArrayList< ImagePlusTimePoint > optimized = GlobalOptimization.optimize( pairs, pairs.get( 0 ).getTile1(), params );
		
		for ( final ImagePlusTimePoint imt : optimized )
			IJ.log( imt.getImagePlus().getTitle() + ": " + imt.getModel() );
	}

	protected static Roi getROI( final ImageCollectionElement e1, final ImageCollectionElement e2 )
	{
		final int start[] = new int[ 2 ], end[] = new int[ 2 ];
		
		for ( int dim = 0; dim < 2; ++dim )
		{			
			// begin of 2 lies inside 1
			if ( e2.offset[ dim ] >= e1.offset[ dim ] && e2.offset[ dim ] <= e1.offset[ dim ] + e1.size[ dim ] )
			{
				start[ dim ] = Math.round( e2.offset[ dim ] - e1.offset[ dim ] );
				
				// end of 2 lies inside 1
				if ( e2.offset[ dim ] + e2.size[ dim ] <= e1.offset[ dim ] + e1.size[ dim ] )
					end[ dim ] = Math.round( e2.offset[ dim ] + e2.size[ dim ] - e1.offset[ dim ] );
				else
					end[ dim ] = Math.round( e1.size[ dim ] );
			}
			else if ( e2.offset[ dim ] + e2.size[ dim ] <= e1.offset[ dim ] + e1.size[ dim ] ) // end of 2 lies inside 1
			{
				start[ dim ] = 0;
				end[ dim ] = Math.round( e2.offset[ dim ] + e2.size[ dim ] - e1.offset[ dim ] );
			}
			else // if both outside then the whole image 
			{
				start[ dim ] = -1;
				end[ dim ] = -1;
			}
		}
		
		return new Roi( new Rectangle( start[ 0 ], start[ 1 ], end[ 0 ] - start[ 0 ], end[ 1 ] - start[ 1 ] ) );
	}

	protected static Vector< ComparePair > findOverlappingTiles( final ArrayList< ImageCollectionElement > elements, final StitchingParameters params )
	{		
		for ( final ImageCollectionElement element : elements )
		{
			if ( element.open() == null )
				return null;
		}
	
		// get the connecting tiles
		final Vector< ComparePair > overlappingTiles = new Vector< ComparePair >();

		for ( int i = 0; i < elements.size() - 1; i++ )
			for ( int j = i + 1; j < elements.size(); j++ )
			{
				final ImageCollectionElement e1 = elements.get( i );
				final ImageCollectionElement e2 = elements.get( j );
				
				boolean overlapping = true;
				
				for ( int d = 0; d < params.dimensionality; ++d )
				{
					if ( !( ( e2.offset[ d ] >= e1.offset[ d ] && e2.offset[ d ] <= e1.offset[ d ] + e1.size[ d ] ) || 
						    ( e2.offset[ d ] + e2.size[ d ] >= e1.offset[ d ] && e2.offset[ d ] + e2.size[ d ] <= e1.offset[ d ] + e1.size[ d ] ) ||
						    ( e2.offset[ d ] <= e1.offset[ d ] && e2.offset[ d ] >= e1.offset[ d ] + e1.size[ d ] ) 
					   )  )
									overlapping = false;
				}
				
				if ( overlapping )
				{
					final ImagePlusTimePoint impA = new ImagePlusTimePoint( e1.open(), e1.getIndex(), 1, e1.getModel(), e1 );
					final ImagePlusTimePoint impB = new ImagePlusTimePoint( e2.open(), e2.getIndex(), 1, e2.getModel(), e2 );
					overlappingTiles.add( new ComparePair( impA, impB ) );
				}
			}
		
		return overlappingTiles;
	}
}