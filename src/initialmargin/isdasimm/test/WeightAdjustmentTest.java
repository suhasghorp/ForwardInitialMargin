package initialmargin.isdasimm.test;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import initialmargin.isdasimm.changedfinmath.LIBORModelMonteCarloSimulationInterface;
import initialmargin.isdasimm.changedfinmath.products.AbstractLIBORMonteCarloProduct;
import initialmargin.isdasimm.changedfinmath.products.Swap;
import initialmargin.isdasimm.changedfinmath.products.SwapLeg;
import initialmargin.isdasimm.changedfinmath.products.components.AbstractNotional;
import initialmargin.isdasimm.changedfinmath.products.components.Notional;
import initialmargin.isdasimm.changedfinmath.products.indices.AbstractIndex;
import initialmargin.isdasimm.changedfinmath.products.indices.LIBORIndex;
import initialmargin.isdasimm.products.AbstractSIMMProduct;
import initialmargin.isdasimm.products.SIMMSimpleSwap;
import initialmargin.isdasimm.sensitivity.AbstractSIMMSensitivityCalculation.SensitivityMode;
import initialmargin.isdasimm.sensitivity.AbstractSIMMSensitivityCalculation.WeightMode;
import initialmargin.isdasimm.sensitivity.SIMMSensitivityCalculation;
import net.finmath.analytic.model.curves.DiscountCurve;
import net.finmath.exception.CalculationException;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.montecarlo.AbstractRandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAAD;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAADFactory;
import net.finmath.optimizer.SolverException;
import net.finmath.stochastic.RandomVariableInterface;
import net.finmath.time.ScheduleGenerator;
import net.finmath.time.ScheduleInterface;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarExcludingTARGETHolidays;

public class WeightAdjustmentTest {
	final static DecimalFormat formatterTime	= new DecimalFormat("0.000");
	final static DecimalFormat formatterIM  	= new DecimalFormat("0.00000000000");
	
	// LIBOR Market Model parameters
	private final static int numberOfPaths		= 1000;
	private final static int numberOfFactors	= 1;
	final private static String[]  IRMaturityBuckets = {"2w","1m","3m","6m","1y","2y","3y","5y","10y","15y","20y","30y"};
		
	public static void main(String[] args) throws SolverException, CloneNotSupportedException, CalculationException {
		
		 
     	 AbstractRandomVariableFactory randomVariableFactory = createRandomVariableFactoryAAD();
     	 
     	 // Create a Libor market Model
     	 RandomVariableInterface[] RVVector = getRVAAD(new double[] {0.996 , 0.995, 0.994, 0.993, 0.98});
 	     double[] discountCurvePillars = new double[] {0.5 , 1.0, 2.0, 5.0, 30.0};
     	 DiscountCurve discountCurve = DiscountCurve.createDiscountCurveFromDiscountFactors("discountCurve",
     			                                                                            discountCurvePillars /*times*/,
     			                                                                            RVVector /*discountFactors*/);
     			                                                                            
     	 ForwardCurve  forwardCurve = ForwardCurve.createForwardCurveFromForwards("forwardCurve",
 					                                                              new double[] {0.5 , 1.0, 2.0, 5.0, 30.0}	/* fixings of the forward */,					                                                            
 					                                                              new double[] {0.02, 0.02, 0.02, 0.02, 0.02},
 					                                                              0.5/* tenor / period length */);
 					
     	 LIBORModelMonteCarloSimulationInterface model = SIMMTest.createLIBORMarketModel(false,randomVariableFactory,numberOfPaths, numberOfFactors, 
     				                                                            discountCurve,
     				                                                            forwardCurve,0.0 /* Correlation */);
     	 
     	 // Create another Libor market model with steeper curves
     	 RandomVariableInterface[] RVVector2 = getRVAAD(new double[] {0.99 , 0.98, 0.97, 0.9, 0.7});
     	 DiscountCurve discountCurveSteep = DiscountCurve.createDiscountCurveFromDiscountFactors("discountCurve2",
     			                                                                                 discountCurvePillars /*times*/,
                                                                                                 RVVector2 /*discountFactors*/);
         ForwardCurve  forwardCurveSteep = ForwardCurve.createForwardCurveFromForwards("forwardCurve2",
                                                                                       new double[] {0.5 , 1.0, 2.0, 5.0, 30.0}	/* fixings of the forward */,
                                                                                       new double[] {0.02, 0.02, 0.02, 0.02, 0.02},
                                                                                       0.5/* tenor / period length */);

         LIBORModelMonteCarloSimulationInterface model2 = SIMMTest.createLIBORMarketModel(false, randomVariableFactory,numberOfPaths, numberOfFactors, 
                                                                                 discountCurveSteep,
                                                                                 forwardCurveSteep, 0.0 /* Correlation */);
        
 		
 	   // Classify the products 
 	   /*
    	*  Create Products. Input for Swap
    	*/
       double     startTime            = 0.0;	// Exercise date
 	   double     constantSwapRateSwap = 0.025;
 	   int        numberOfPeriodsSwap  = 10;
 	   double     notionalSwap        = 100;
   	   double[]   fixingDatesSwap     = new double[numberOfPeriodsSwap];
   	   double[]   paymentDatesSwap    = new double[numberOfPeriodsSwap];  	
   	   double[]   swapRatesSwap       = new double[numberOfPeriodsSwap];
   	   
   	   // Fill data
   	   fixingDatesSwap = IntStream.range(0, fixingDatesSwap.length).mapToDouble(i->startTime+i*0.5).toArray();
   	   paymentDatesSwap = IntStream.range(0, paymentDatesSwap.length).mapToDouble(i->startTime+(i+1)*0.5).toArray();
   	   Arrays.fill(swapRatesSwap, constantSwapRateSwap); 
 	   AbstractSIMMProduct SIMMSwap = new SIMMSimpleSwap(fixingDatesSwap, paymentDatesSwap, swapRatesSwap, true /*isPayFix*/,notionalSwap, new String[]{"OIS", "Libor6m"}, "EUR", false /*useAnalyticSensis*/);
 	   
 	   // Create calculation 
 	   SIMMSensitivityCalculation sc1 = new SIMMSensitivityCalculation(SensitivityMode.Exact, WeightMode.Constant, 0, model);
 	   SIMMSensitivityCalculation ss1 = new SIMMSensitivityCalculation(SensitivityMode.Exact, WeightMode.Stochastic, 0, model);
 	   SIMMSensitivityCalculation sc2 = new SIMMSensitivityCalculation(SensitivityMode.Exact, WeightMode.Constant, 0, model2);
 	   SIMMSensitivityCalculation ss2 = new SIMMSensitivityCalculation(SensitivityMode.Exact, WeightMode.Stochastic, 0, model2);

 	  	   
		
		// --------------------------------------------------------------------------------------------------------------------------
		// 1) Compare swap rate forward sensis dV/dS using stochastic dL/dS and constant dL/dS
		// --------------------------------------------------------------------------------------------------------------------------
		
		double initialMarginTime = 1.5;
		
		// Get dVdS(t) = dVdL(t)*dLdS(t) with weakly stochastic dLdS(t)
		SIMMSwap.setSIMMSensitivityCalculation(ss1);
		RandomVariableInterface[] dVdS_Stochastic = ss1.doCalculateDeltaSensitivitiesIR(SIMMSwap, "Libor6m", initialMarginTime, model);
		// Get dVdS(t) = dVdL(t)*dLdS(t=0) with constant dLdS(t=0) 
		SIMMSwap.setSIMMSensitivityCalculation(sc1);
		RandomVariableInterface[] dVdS_Constant = sc1.doCalculateDeltaSensitivitiesIR(SIMMSwap, "Libor6m", initialMarginTime, model);
		
		// Check what happens when using the other (steeper) discount curve
		SIMMSwap.setSIMMSensitivityCalculation(ss2);
		RandomVariableInterface[] dVdS_Stochastic2 = ss2.doCalculateDeltaSensitivitiesIR(SIMMSwap, "Libor6m", initialMarginTime, model2);
		SIMMSwap.setSIMMSensitivityCalculation(sc2);
		RandomVariableInterface[] dVdS_Constant2 = sc2.doCalculateDeltaSensitivitiesIR(SIMMSwap, "Libor6m", initialMarginTime, model2);

		System.out.println("Comparison of swap rate forward sensis at time " + formatterTime.format(initialMarginTime) + "for flat and steep discount curve");
		System.out.println("Bucket     " + "\t" + "dVdS stochastic flat    " + "\t" +  "dVdS constant flat" + 
		                                       "\t" + "dVdS stochastic steep   " + "\t" +  "dVdS constant steep");
		
		for(int swapIndex = 0; swapIndex<dVdS_Stochastic.length; swapIndex++){
	       System.out.println(IRMaturityBuckets[swapIndex] + "      \t" + 
			                  formatterIM.format(dVdS_Stochastic[swapIndex].getAverage())+ "   \t" +
			                  formatterIM.format(dVdS_Constant[swapIndex].getAverage()) + "    \t" +
			                  formatterIM.format(dVdS_Stochastic2[swapIndex].getAverage())+ "  \t" +
			                  formatterIM.format(dVdS_Constant2[swapIndex].getAverage()));
		
		}
	
		System.out.println("---------------------------------------------------------------------------------------------------------");
		
		// --------------------------------------------------------------------------------------------------------------------------
		// 2) Calculate forward Initial Margin over time 
		// --------------------------------------------------------------------------------------------------------------------------
		System.out.println("Forward Initial Margin over time");
				
		double finalTime = 5.0; // The last time of the IM exposure to be calculated 
		double timeStep  = 0.125;
		
		// Perform Calculations
		// 1) Stochastic
		double[] IM_Stochastic = new double[(int)(finalTime/timeStep)];
		long timeStochasticStart = System.currentTimeMillis();
		  for(int i=0;i<IM_Stochastic.length;i++) IM_Stochastic[i] = SIMMSwap.getInitialMargin(i*timeStep, model, "EUR", SensitivityMode.Exact, WeightMode.Stochastic, 0).getAverage();
		long timeStochasticEnd   = System.currentTimeMillis();
		
		// 2) Constant
		double[] IM_Constant = new double[(int)(finalTime/timeStep)];
		long timeConstantStart = System.currentTimeMillis();
		  for(int i=0;i<IM_Constant.length;i++) IM_Constant[i] = SIMMSwap.getInitialMargin(i*timeStep, model, "EUR", SensitivityMode.Exact, WeightMode.Constant, 0).getAverage();
		long timeConstantEnd   = System.currentTimeMillis();
		
		// Printing results
		System.out.println("Calculation Time Stochastic = " + formatterTime.format((timeStochasticEnd-timeStochasticStart)/1000.0) + "s");
		System.out.println("Calculation Time Constant   = " + formatterTime.format((timeConstantEnd-timeConstantStart)/1000.0) + "s");
		System.out.println("Bucket" + "\t" + "IM Stochastic Weights" + "\t" +   "IM Constant Weights");	
		for(int i = 0; i<(finalTime/timeStep);i++){
		   double forwardIMTime = i*timeStep;
		   System.out.println(formatterTime.format(forwardIMTime) + "\t" +
		                      formatterIM.format(IM_Stochastic[i])  + "      \t" +
		                      formatterIM.format(IM_Constant[i]));
		}
		
		
		
	}
	
		public static AbstractLIBORMonteCarloProduct[] createSwaps(String[] maturities){
		    AbstractLIBORMonteCarloProduct[] swaps = new AbstractLIBORMonteCarloProduct[maturities.length];
		    // 1) Create Portfolio of swaps -------------------------------------------------------------------------------
		    for(int swapIndex = 0; swapIndex < maturities.length; swapIndex++){
		       // Floating Leg
			   LocalDate	referenceDate = LocalDate.of(2017, 8, 12);
			   int			spotOffsetDays = 0;
			   String		forwardStartPeriod = "0D";
			   String		maturity = maturities[swapIndex];
			   String		frequency = "semiannual";
			   String		daycountConvention = "30/360";

			   /*
			    * Create Monte-Carlo leg
			    */
			   AbstractNotional notional = new Notional(100.0);//*(1+Math.max(Math.random(), -0.7)));
			   AbstractIndex index = new LIBORIndex(0.0, 0.5);
			   double spread = 0.0;
			   ScheduleInterface schedule = ScheduleGenerator.createScheduleFromConventions(referenceDate, spotOffsetDays, forwardStartPeriod, maturity, frequency, daycountConvention, "first", "following", new BusinessdayCalendarExcludingTARGETHolidays(), 0, 0);
			   SwapLeg leg = new SwapLeg(schedule, notional, index, spread, false /* isNotionalExchanged */);
		    
			   // Fixed Leg
			   LocalDate	referenceDateF = LocalDate.of(2017, 8, 12);
			   int			spotOffsetDaysF = 0;
			   String		forwardStartPeriodF = "0D";
			   String		maturityF = maturities[swapIndex];
			   String		frequencyF = "semiannual";
			   String		daycountConventionF = "30/360";

			   /*
			    * Create Monte-Carlo leg
			    */
			   AbstractNotional notionalF = notional;
			   AbstractIndex indexF = null;
			   double spreadF = 0.02;
			   ScheduleInterface scheduleF = ScheduleGenerator.createScheduleFromConventions(referenceDateF, spotOffsetDaysF, forwardStartPeriodF, maturityF, frequencyF, daycountConventionF, "first", "following", new BusinessdayCalendarExcludingTARGETHolidays(), 0, 0);
			   SwapLeg legF = new SwapLeg(scheduleF, notionalF, indexF, spreadF, false /* isNotionalExchanged */);

			   // Swap
			   AbstractLIBORMonteCarloProduct swap = new Swap(leg,legF);
			   swaps[swapIndex]=swap;
		    }
		  return swaps;
		}
		 
		public static AbstractRandomVariableFactory createRandomVariableFactoryAAD(){
		   Map<String, Object> properties = new HashMap<String, Object>();
		   properties.put("isGradientRetainsLeafNodesOnly", new Boolean(false));
		   return new RandomVariableDifferentiableAADFactory(new RandomVariableFactory(), properties);
		}
		
		public static RandomVariableInterface[] getRVAAD(double[] rates){
			RandomVariableInterface[] rv = new RandomVariableInterface[rates.length];
			for(int i=0;i<rv.length;i++) rv[i]=new RandomVariableDifferentiableAAD(rates[i]);
			return rv;
		}
		
	}
	
   
   
