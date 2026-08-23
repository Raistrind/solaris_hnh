package haven;

import java.awt.image.BufferedImage;

public class MinimapPanel extends Window {

	static final BufferedImage grip = Resource.loadimg("gfx/hud/gripbr");
	static final Coord gzsz = new Coord(16,17);
	static final Coord minsz = new Coord(150, 125);
	public IButton vcl;
	public IButton pcl;
	boolean rsm = false;
	boolean rightAnchored = true;
	MiniMap mm;
	IButton btncave;

	public MinimapPanel(Coord c, Coord sz, Widget parent) {
		super(c, sz, parent, "Map");
		mrgn = Coord.z;
		fbtn.visible = true;
		cbtn.visible = false;
		{
			new IButton(new Coord(-3, -2), this, Resource.loadimg("gfx/hud/slen/dispauth"), Resource.loadimg("gfx/hud/slen/dispauthd")) {
				private boolean v = false;
				public void click() {
					MapView mv = ui.mapview;
					BufferedImage tmp = down;
					down = up;
					up = tmp;
					hover = tmp;
					if(v) {
						mv.disol(2, 3);
						v = false;
					} else {
						mv.enol(2, 3);
						v = true;
					}
				}

				private Text tooltip = Text.render("Display Village Claims");
				@Override
				public Object tooltip(Coord c, boolean again) {
					return checkhit(c)?tooltip:null;
				}
			};
		}
		{
			pcl = new IButton(new Coord(0, 4), this, Resource.loadimg("gfx/hud/slen/dispclaim"), Resource.loadimg("gfx/hud/slen/dispclaimd")) {
				private boolean v = false;

				public void click() {
					MapView mv = ui.mapview;
					BufferedImage tmp = down;
					down = up;
					up = tmp;
					hover = tmp;
					if(v) {
						mv.disol(0, 1);
						v = false;
					} else {
						mv.enol(0, 1);
						v = true;
					}
				}
			};
			pcl.tooltip = "Show Claims";
		}

		ui.minimappanel = this;
		mm = new MiniMap(new Coord(0, 32), minsz, this, ui.mapview);

		new IButton(new Coord(45, 8), this, Resource.loadimg("gfx/hud/buttons/gridu"), Resource.loadimg("gfx/hud/buttons/gridd")) {
			public void click() {
				BufferedImage tmp = down;
				down = up;
				up = tmp;
				hover = tmp;
				mm.grid = !mm.grid;
			}
		}.tooltip = "Show minimap grid";

		new IButton(new Coord(65, 8), this, Resource.loadimg("gfx/hud/buttons/centeru"), Resource.loadimg("gfx/hud/buttons/centerd")) {
			public void click() {
				mm.off = new Coord();
				UI.instance.mapview.resetcam(); //Kerrigan
			}
		}.tooltip = "Focus minimap and character";

		new IButton(new Coord(88, 12), this, Resource.loadimg("gfx/hud/charsh/plusup"), Resource.loadimg("gfx/hud/charsh/plusdown")) {
			public void click() {
				mm.setScale(mm.scale+1);
			}
		};

		new IButton(new Coord(103, 12), this, Resource.loadimg("gfx/hud/charsh/minusup"), Resource.loadimg("gfx/hud/charsh/minusdown")) {
			public void click() {
				mm.setScale(mm.scale-1);
			}
		};

		btncave = new IButton(new Coord(121, 8), this, Resource.loadimg("gfx/hud/buttons/saveu"), Resource.loadimg("gfx/hud/buttons/saved")) {
			public void click() {
				mm.saveCaveMaps();
			}
		};

		pack();
		this.c = new Coord(MainFrame.getInnerSize().x - getDisplaySize().x, 7);
		loadpos();

	}

	public double getDisplayScale() {
		return fitDisplayScale(Config.elementScale(Config.minimapScale),
				(cap == null) ? sz : sz.add(0, 7));
	}

	private void loadpos(){
		synchronized (Config.window_props) {
			String savedPosition = Config.window_props.getProperty("minimap_pos");
			if (savedPosition != null) {
				c = new Coord(savedPosition);
				rightAnchored = false;
			}
			mm.sz = new Coord(Config.window_props.getProperty("minimap_sz", mm.sz.toString()));
			pack();
		}
	}

	protected void placecbtn() {
		fbtn.c = new Coord(wsz.x - 3 - Utils.imgsz(cbtni[0]).x, 3).add(mrgn.inv().add(wbox.tloff().inv()));
	}

	public void update(long dt) {
		if (rightAnchored)
			c.x = MainFrame.getInnerSize().x - getDisplaySize().x;
		super.update(dt);
	}

	public void draw(GOut g) {
		super.draw(g);
		btncave.visible = !folded && mm.isCave();
		if(!folded)
			g.image(grip, sz.sub(gzsz));
	}

	public boolean mousedown(Coord c, int button) {
		if(folded) {
			return super.mousedown(c, button);
		}
		parent.setfocus(this);
		raise();
		if (button == 1) {
			ui.grabmouse(this);
			doff = c;
			if(c.isect(sz.sub(gzsz), gzsz)) {
				rsm = true;
				return true;
			}
		}
		boolean handled = super.mousedown(c, button);
		if (dm)
			rightAnchored = false;
		return handled;
	}

	public boolean mouseup(Coord c, int button) {
		if(dm){
			rightAnchored = false;
			Config.setWindowOpt("minimap_pos", this.c.toString());
		}
		if (rsm){
			ui.grabmouse(null);
			rsm = false;
			Config.setWindowOpt("minimap_sz", mm.sz.toString());
		} else {
			super.mouseup(c, button);
		}
		return (true);
	}

	public void mousemove(Coord c) {
		if (rsm){
			Coord d = c.sub(doff);
			mm.sz = mm.sz.add(d);
			mm.sz.x = Math.max(minsz.x, mm.sz.x);
			mm.sz.y = Math.max(minsz.y, mm.sz.y);
			doff = c;
			pack();
		} else {
			super.mousemove(c);
		}
	}

	public boolean type(char key, java.awt.event.KeyEvent ev) {
		if(key == 27) {
			wdgmsg(fbtn, "click");
			return(true);
		}
		return(super.type(key, ev));
	}

}
