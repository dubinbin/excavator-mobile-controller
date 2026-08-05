
import "./App.css";

function App() {
  return (
    <section id="center" className="module-loading-page">
      <div className="module-loading" role="status" aria-live="polite">
        <div className="module-loading__spinner" aria-hidden="true" />
        <div className="module-loading__content">
          <h1>正在热加载模块</h1>
          <p className="module-loading__title-en">Hot-loading modules</p>
          <div className="module-loading__divider" aria-hidden="true" />
          <p className="module-loading__hint">首次加载需要 3–5 秒</p>
          <p className="module-loading__hint-en">
            The first load may take 3–5 seconds.
          </p>
        </div>
      </div>
    </section>
  );
}

export default App;
