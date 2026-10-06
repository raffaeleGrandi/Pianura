package jusePack.gui;

import java.awt.BorderLayout;
import java.awt.Point;
import javax.swing.JFrame;

import jusePack.sim.SimEngine;
import jusePack.structures.ScenarioManager;

/** Finestra principale: ridimensionabile, la vista dell'arena si adatta. */
@SuppressWarnings("serial")
public class JuseMF extends JFrame{

	public SimPanel simPanel;

	public JuseMF(String frameTitle, Point frameLoc, String logInitStr, ScenarioManager scenario, SimEngine engine){
		super(frameTitle);
		getContentPane().setLayout(new BorderLayout());
		simPanel = new SimPanel(logInitStr, scenario, engine);
		getContentPane().add(simPanel, BorderLayout.CENTER);
		pack();
		setLocation(frameLoc);
		setResizable(true);
		setVisible(true);
	}

	public void showMSG(boolean setClear, String msg){ simPanel.showMSG(setClear, msg); }

	public void startRefresh(){simPanel.startRefresh();}

}
