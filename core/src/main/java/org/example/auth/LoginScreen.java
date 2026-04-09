package org.example.auth;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import org.example.hud.UIUtils;

public class LoginScreen {

    private enum View { LOGIN, REGISTER, FORGOT_PASSWORD }

    public interface Callback {
        void onLoginSuccess(AuthService.AuthResult result);
    }

    private final Stage          stage;
    private final Skin           skin;
    private final AuthService    authService;
    private final UserSession    userSession;
    private final Callback       callback;

    // Separate smaller font for text field content — the game font is large by design.
    // Loaded once and disposed with the screen.
    private final BitmapFont        fieldFont;
    private final TextField.TextFieldStyle fieldStyle;

    private View    currentView     = View.LOGIN;
    private boolean busy            = false;
    private String  lastEmail       = "";
    private Label   activeErrorLabel = null;

    // -------------------------------------------------------------------------

    public LoginScreen(Skin skin, AuthService authService, UserSession userSession, Callback callback) {
        this.skin        = skin;
        this.authService = authService;
        this.userSession = userSession;
        this.callback    = callback;
        this.lastEmail   = userSession.getEmail();
        this.stage       = new Stage(new ExtendViewport(1280, 720));

        // Build a smaller font for text fields by loading the same font file at a reduced scale.
        this.fieldFont = new BitmapFont(Gdx.files.internal("font/golf.fnt"));
        this.fieldFont.getData().setScale(0.60f);

        // Copy the registered TextField style but swap in the smaller font.
        TextField.TextFieldStyle base = skin.get(TextField.TextFieldStyle.class);
        this.fieldStyle = new TextField.TextFieldStyle();
        fieldStyle.font             = fieldFont;
        fieldStyle.fontColor        = base.fontColor != null ? base.fontColor : Color.WHITE;
        fieldStyle.background       = base.background;
        fieldStyle.cursor           = base.cursor;
        fieldStyle.selection        = base.selection;
        fieldStyle.messageFontColor = Color.GRAY;
        fieldStyle.messageFont      = fieldFont;

        buildUI();
    }

    public Stage getStage() { return stage; }

    /** Resets to the default login view with cleared fields. Call this on logout. */
    public void reset() {
        currentView = View.LOGIN;
        lastEmail = "";
        buildUI();
    }

    public void resize(int w, int h) { stage.getViewport().update(w, h, true); }

    public void render() {
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    public void dispose() {
        stage.dispose();
        fieldFont.dispose();
    }

    // -------------------------------------------------------------------------
    // UI construction — rebuilt on every view switch
    // -------------------------------------------------------------------------

    private void buildUI() {
        stage.clear();
        activeErrorLabel = null;
        busy = false;

        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;
        float panelW     = isAndroid ? 560f  : 460f;
        float fieldW     = panelW - 60f;
        float fieldH     = isAndroid ? 75f   : 62f;   // taller than buttons so field font fits well
        float btnH       = isAndroid ? 68f   : 54f;
        float pad        = 24f;
        float sp         = 10f;
        float labelScale = isAndroid ? 0.95f : 1.05f;
        float btnScale   = isAndroid ? 0.88f : 0.95f;
        float titleScale = isAndroid ? 1.20f : 1.35f;

        // Full-screen dim overlay
        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(UIUtils.createRoundedRectDrawable(new Color(0f, 0f, 0f, 0.65f), 0));

        // Centred panel
        Table panel = new Table();
        panel.setBackground(UIUtils.createGoldBorderedPanel(new Color(0.05f, 0.05f, 0.05f, 0.97f), 3));
        panel.pad(pad);

        Label title = new Label("GEARY GOLF", skin, "default");
        title.setFontScale(titleScale);
        title.setColor(Color.GOLD);
        title.setAlignment(Align.center);
        panel.add(title).expandX().fillX().padBottom(sp).row();

        String heading = switch (currentView) {
            case LOGIN           -> "SIGN IN";
            case REGISTER        -> "CREATE ACCOUNT";
            case FORGOT_PASSWORD -> "RESET PASSWORD";
        };
        Label subLbl = new Label(heading, skin, "default");
        subLbl.setFontScale(labelScale * 0.88f);
        subLbl.setColor(Color.LIGHT_GRAY);
        subLbl.setAlignment(Align.center);
        panel.add(subLbl).expandX().fillX().padBottom(pad).row();

        Table content = new Table();
        switch (currentView) {
            case LOGIN           -> buildLoginView   (content, fieldW, fieldH, btnH, pad, sp, labelScale, btnScale);
            case REGISTER        -> buildRegisterView(content, fieldW, fieldH, btnH, pad, sp, labelScale, btnScale);
            case FORGOT_PASSWORD -> buildForgotView  (content, fieldW, fieldH, btnH, pad, sp, labelScale, btnScale);
        }
        panel.add(content).width(fieldW + 40f).row();

        Label errorLbl = new Label("", skin, "default");
        errorLbl.setFontScale(labelScale * 0.82f);
        errorLbl.setColor(new Color(1f, 0.35f, 0.35f, 1f));
        errorLbl.setAlignment(Align.center);
        errorLbl.setWrap(true);
        panel.add(errorLbl).width(fieldW + 40f).padTop(sp).row();
        activeErrorLabel = errorLbl;

        root.add(panel).width(panelW);
        stage.addActor(root);
    }

    // -------------------------------------------------------------------------
    // Login view
    // -------------------------------------------------------------------------

    private void buildLoginView(Table t, float fw, float fh, float bh, float pad, float sp,
                                float ls, float bs) {
        TextField emailField = makeField(lastEmail, "email@example.com", false);
        TextField pwField    = makeField("", "password", true);

        TextButton loginBtn    = makeButton("LOGIN", bs);
        TextButton registerBtn = makeLinkBtn("Create Account", ls * 0.82f);
        TextButton forgotBtn   = makeLinkBtn("Forgot Password?", ls * 0.82f);

        t.add(makeLabel("Email", ls)).left().padBottom(3).row();
        t.add(emailField).width(fw).height(fh).padBottom(sp).row();
        t.add(makeLabel("Password", ls)).left().padBottom(3).row();
        t.add(pwField).width(fw).height(fh).padBottom(pad).row();
        t.add(loginBtn).width(fw).height(bh).padBottom(sp).row();

        Table links = new Table();
        links.add(registerBtn).expandX().left();
        links.add(forgotBtn).expandX().right();
        t.add(links).width(fw).padBottom(sp).row();

        Runnable doLogin = () -> {
            String email = emailField.getText().trim();
            String pw    = pwField.getText();
            if (email.isEmpty() || pw.isEmpty()) { showError("Please enter your email and password."); return; }
            setBusy(true);
            lastEmail = email;
            authService.signIn(email, pw, new AuthService.AuthCallback() {
                @Override public void onSuccess(AuthService.AuthResult r) {
                    userSession.save(r);
                    callback.onLoginSuccess(r);
                }
                @Override public void onFailure(String msg) { setBusy(false); showError(msg); }
            });
        };

        pwField.addListener(new InputListener() {
            @Override public boolean keyDown(InputEvent e, int key) {
                if (key == Input.Keys.ENTER) { doLogin.run(); return true; }
                return false;
            }
        });
        loginBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { if (!busy) doLogin.run(); }
        });
        registerBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!busy) { currentView = View.REGISTER; buildUI(); }
            }
        });
        forgotBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!busy) { currentView = View.FORGOT_PASSWORD; buildUI(); }
            }
        });

        stage.setKeyboardFocus(emailField.getText().isEmpty() ? emailField : pwField);
    }

    // -------------------------------------------------------------------------
    // Register view
    // -------------------------------------------------------------------------

    private void buildRegisterView(Table t, float fw, float fh, float bh, float pad, float sp,
                                   float ls, float bs) {
        TextField nameField  = makeField("", "your name", false);
        TextField emailField = makeField(lastEmail, "email@example.com", false);
        TextField pwField    = makeField("", "password", true);

        TextButton createBtn = makeButton("CREATE ACCOUNT", bs);
        TextButton backBtn   = makeLinkBtn("← Back", ls * 0.82f);

        t.add(makeLabel("Display Name", ls)).left().padBottom(3).row();
        t.add(nameField).width(fw).height(fh).padBottom(sp).row();
        t.add(makeLabel("Email", ls)).left().padBottom(3).row();
        t.add(emailField).width(fw).height(fh).padBottom(sp).row();
        t.add(makeLabel("Password (6+ characters)", ls)).left().padBottom(3).row();
        t.add(pwField).width(fw).height(fh).padBottom(pad).row();
        t.add(createBtn).width(fw).height(bh).padBottom(sp).row();
        t.add(backBtn).left().row();

        Runnable doRegister = () -> {
            String name  = nameField.getText().trim();
            String email = emailField.getText().trim();
            String pw    = pwField.getText();
            if (name.isEmpty())  { showError("Please enter a display name."); return; }
            if (email.isEmpty()) { showError("Please enter your email."); return; }
            if (pw.length() < 6) { showError("Password must be at least 6 characters."); return; }
            setBusy(true);
            lastEmail = email;
            authService.signUp(email, pw, name, new AuthService.AuthCallback() {
                @Override public void onSuccess(AuthService.AuthResult r) {
                    userSession.save(r);
                    callback.onLoginSuccess(r);
                }
                @Override public void onFailure(String msg) { setBusy(false); showError(msg); }
            });
        };

        createBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { if (!busy) doRegister.run(); }
        });
        backBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!busy) { currentView = View.LOGIN; buildUI(); }
            }
        });

        stage.setKeyboardFocus(nameField);
    }

    // -------------------------------------------------------------------------
    // Forgot password view
    // -------------------------------------------------------------------------

    private void buildForgotView(Table t, float fw, float fh, float bh, float pad, float sp,
                                 float ls, float bs) {
        Label info = makeLabel("Enter your email and we'll send a reset link.", ls * 0.85f);
        info.setWrap(true);
        info.setColor(Color.LIGHT_GRAY);

        TextField emailField = makeField(lastEmail, "email@example.com", false);
        TextButton sendBtn   = makeButton("SEND RESET EMAIL", bs);
        TextButton backBtn   = makeLinkBtn("← Back", ls * 0.82f);

        Label successLbl = makeLabel("", ls * 0.85f);
        successLbl.setColor(new Color(0.35f, 1f, 0.35f, 1f));
        successLbl.setWrap(true);
        successLbl.setAlignment(Align.center);

        t.add(info).width(fw).padBottom(pad).row();
        t.add(emailField).width(fw).height(fh).padBottom(sp).row();
        t.add(sendBtn).width(fw).height(bh).padBottom(sp).row();
        t.add(successLbl).width(fw).padBottom(sp).row();
        t.add(backBtn).left().row();

        sendBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (busy) return;
                String email = emailField.getText().trim();
                if (email.isEmpty()) { showError("Please enter your email."); return; }
                setBusy(true);
                lastEmail = email;
                authService.sendPasswordReset(email, new AuthService.SimpleCallback() {
                    @Override public void onSuccess() {
                        setBusy(false);
                        successLbl.setText("If an account exists for that email, a reset link has been sent — check your inbox (and spam folder).");
                        sendBtn.setVisible(false);
                    }
                    @Override public void onFailure(String msg) { setBusy(false); showError(msg); }
                });
            }
        });
        backBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!busy) { currentView = View.LOGIN; buildUI(); }
            }
        });

        stage.setKeyboardFocus(emailField);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showError(String msg) {
        if (activeErrorLabel != null) activeErrorLabel.setText(msg);
    }

    private void setBusy(boolean b) { this.busy = b; }

    private Label makeLabel(String text, float scale) {
        Label lbl = new Label(text, skin, "default");
        lbl.setFontScale(scale);
        return lbl;
    }

    private TextButton makeButton(String text, float scale) {
        TextButton btn = new TextButton(text, skin, "default");
        btn.getLabel().setFontScale(scale);
        return btn;
    }

    private TextButton makeLinkBtn(String text, float scale) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font          = skin.getFont("default-font");
        style.fontColor     = new Color(0.55f, 0.78f, 1f, 1f);
        style.overFontColor = Color.WHITE;
        style.downFontColor = Color.GRAY;
        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(scale);
        return btn;
    }

    private TextField makeField(String text, String placeholder, boolean password) {
        TextField field = new TextField(text, fieldStyle);
        field.setMessageText(placeholder);
        if (password) {
            field.setPasswordMode(true);
            field.setPasswordCharacter('*');
        }
        return field;
    }
}
