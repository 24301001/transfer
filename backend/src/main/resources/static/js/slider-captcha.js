(function (global) {
  "use strict";

  class SliderCaptchaModal {
    constructor(options) {
      this.options = Object.assign({
        baseUrl: "",
        challengePath: "/api/v1/auth/slider-captcha/challenge",
        verifyPath: "/api/v1/auth/slider-captcha/verify"
      }, options || {});
      this.challenge = null;
      this.resolve = null;
      this.reject = null;
      this.dragging = false;
      this.startClientX = 0;
      this.startHandleX = 0;
      this.handleX = 0;
      this.build();
    }

    open() {
      this.overlay.style.display = "flex";
      this.message.textContent = "";
      this.resetPosition();
      this.loadChallenge();
      return new Promise((resolve, reject) => {
        this.resolve = resolve;
        this.reject = reject;
      });
    }

    close(reason) {
      this.overlay.style.display = "none";
      if (reason && this.reject) {
        this.reject(new Error(reason));
      }
      this.resolve = null;
      this.reject = null;
    }

    async loadChallenge() {
      this.message.textContent = "正在加载…";
      try {
        const response = await fetch(this.options.baseUrl + this.options.challengePath, {
          method: "POST",
          credentials: "include",
          headers: { "Accept": "application/json" }
        });
        const data = await this.readJson(response);
        if (!response.ok) throw new Error(data.message || "滑块验证码加载失败");
        this.challenge = data;
        this.background.src = data.backgroundImageBase64;
        this.piece.src = data.puzzleImageBase64;
        this.background.onload = () => {
          const ratio = this.stage.clientWidth / data.imageWidth;
          this.piece.style.width = (data.pieceWidth * ratio) + "px";
          this.piece.style.height = (data.pieceWidth * ratio) + "px";
          this.piece.style.top = (data.puzzleY * ratio) + "px";
          this.resetPosition();
        };
        this.message.textContent = "";
      } catch (error) {
        this.message.textContent = error.message;
      }
    }

    async verify() {
      if (!this.challenge) return;
      const imageTravel = this.challenge.imageWidth - this.challenge.pieceWidth;
      const handleTravel = Math.max(1, this.track.clientWidth - this.handle.clientWidth);
      const sliderX = Math.round((this.handleX / handleTravel) * imageTravel);
      this.message.textContent = "正在验证…";

      try {
        const response = await fetch(this.options.baseUrl + this.options.verifyPath, {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
            "Accept": "application/json"
          },
          body: JSON.stringify({
            captchaId: this.challenge.captchaId,
            sliderX: sliderX
          })
        });
        const data = await this.readJson(response);
        if (!response.ok) throw new Error(data.message || "滑块验证失败");
        const resolve = this.resolve;
        this.overlay.style.display = "none";
        this.resolve = null;
        this.reject = null;
        if (resolve) resolve(data.sliderToken);
      } catch (error) {
        this.message.textContent = error.message;
        this.resetPosition();
        await this.loadChallenge();
      }
    }

    resetPosition() {
      this.handleX = 0;
      this.handle.style.transform = "translateX(0px)";
      this.progress.style.width = "0px";
      this.piece.style.left = "0px";
    }

    updatePosition(nextX) {
      const handleTravel = Math.max(0, this.track.clientWidth - this.handle.clientWidth);
      this.handleX = Math.max(0, Math.min(handleTravel, nextX));
      this.handle.style.transform = `translateX(${this.handleX}px)`;
      this.progress.style.width = (this.handleX + this.handle.clientWidth / 2) + "px";

      if (this.challenge) {
        const imageTravel = this.stage.clientWidth - this.piece.clientWidth;
        const pieceX = handleTravel === 0 ? 0 : (this.handleX / handleTravel) * imageTravel;
        this.piece.style.left = pieceX + "px";
      }
    }

    build() {
      this.overlay = document.createElement("div");
      this.overlay.className = "slider-captcha-overlay";
      this.overlay.style.display = "none";
      this.overlay.innerHTML = `
        <div class="slider-captcha-dialog" role="dialog" aria-modal="true" aria-label="滑块安全验证">
          <div class="slider-captcha-header">
            <h3 class="slider-captcha-title">请完成安全验证</h3>
            <div>
              <button class="slider-captcha-refresh" type="button" title="刷新">↻</button>
              <button class="slider-captcha-close" type="button" title="关闭">×</button>
            </div>
          </div>
          <div class="slider-captcha-stage">
            <img class="slider-captcha-background" alt="滑块验证码背景">
            <img class="slider-captcha-piece" alt="拼图块">
          </div>
          <div class="slider-captcha-track">
            <div class="slider-captcha-progress"></div>
            <div class="slider-captcha-hint">拖动滑块完成拼图</div>
            <button class="slider-captcha-handle" type="button" aria-label="拖动滑块">⇥</button>
          </div>
          <div class="slider-captcha-message"></div>
        </div>`;
      document.body.appendChild(this.overlay);

      this.stage = this.overlay.querySelector(".slider-captcha-stage");
      this.background = this.overlay.querySelector(".slider-captcha-background");
      this.piece = this.overlay.querySelector(".slider-captcha-piece");
      this.track = this.overlay.querySelector(".slider-captcha-track");
      this.handle = this.overlay.querySelector(".slider-captcha-handle");
      this.progress = this.overlay.querySelector(".slider-captcha-progress");
      this.message = this.overlay.querySelector(".slider-captcha-message");

      this.overlay.querySelector(".slider-captcha-close")
        .addEventListener("click", () => this.close("用户取消验证"));
      this.overlay.querySelector(".slider-captcha-refresh")
        .addEventListener("click", () => this.loadChallenge());

      this.handle.addEventListener("pointerdown", (event) => {
        this.dragging = true;
        this.startClientX = event.clientX;
        this.startHandleX = this.handleX;
        this.handle.setPointerCapture(event.pointerId);
      });
      this.handle.addEventListener("pointermove", (event) => {
        if (!this.dragging) return;
        this.updatePosition(this.startHandleX + event.clientX - this.startClientX);
      });
      this.handle.addEventListener("pointerup", () => {
        if (!this.dragging) return;
        this.dragging = false;
        this.verify();
      });
      this.handle.addEventListener("pointercancel", () => {
        this.dragging = false;
        this.resetPosition();
      });
    }

    async readJson(response) {
      const text = await response.text();
      if (!text) return {};
      try { return JSON.parse(text); } catch (_) { return { message: text }; }
    }
  }

  global.SliderCaptchaModal = SliderCaptchaModal;
})(window);
