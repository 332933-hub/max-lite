package ru.maxlite.app

/**
 * Лечит горизонтальное «расползание» страницы: длинная ссылка или слово без
 * пробелов в сообщении растягивает флекс-контейнер чата шире экрана, страница
 * начинает скроллиться вбок, и поле ввода с кнопкой «отправить» уезжает за край.
 *
 * Причина в вёрстке сайта, а не в WebView, поэтому чиним стилями:
 *  - overflow-wrap:anywhere разрешает рвать длинную «неразрывную» строку,
 *    а заодно сводит её min-content ширину к одному символу — флекс-элемент
 *    с таким текстом снова умеет ужиматься до ширины колонки;
 *  - html/body ограничиваем шириной экрана, чтобы боковой скролл не появлялся.
 *
 * Здесь НЕ должно быть `* { min-width: 0 }`: правило снимает авто-минимум и с
 * круглых элементов (аватар, кнопка отправки) — те сжимаются в овал. Ширину
 * распирает текст, его и лечим.
 *
 * Правило по `*` задано без !important и с нулевой специфичностью: любое
 * собственное правило сайта его перебьёт, так что вёрстка MAX не ломается.
 */
object WidthFix {

    private const val CSS = """
html, body { max-width: 100% !important; overflow-x: hidden !important; }
* { overflow-wrap: anywhere; }
img, video, canvas, iframe, table, pre { max-width: 100% !important; }
"""

    /**
     * MAX — SPA: страница грузится один раз, дальше DOM перестраивается сама.
     * Поэтому не просто вставляем <style>, а следим, чтобы его не вымели.
     * Наблюдатели без subtree — реагируют только на замену head/body целиком
     * или на удаление самого стиля, то есть почти никогда (в чате мутаций много,
     * подписываться на всё поддерево было бы дорого).
     */
    val SCRIPT = """
        (function () {
          var id = 'maxlite-width-fix';
          var css = `$CSS`;
          function inject() {
            if (document.getElementById(id)) return;
            var s = document.createElement('style');
            s.id = id;
            s.textContent = css;
            (document.head || document.documentElement).appendChild(s);
          }
          inject();
          if (!window.__maxliteWidthFix) {
            window.__maxliteWidthFix = true;
            var obs = new MutationObserver(inject);
            obs.observe(document.documentElement, { childList: true });
            if (document.head) obs.observe(document.head, { childList: true });
          }
        })();
    """
}
