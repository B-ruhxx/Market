import { auth } from '/assets/js/auth.js';
import { renderNavbar } from '/assets/js/ui/navbar.js';

renderNavbar();

const form = document.getElementById('register-form');
const nombresInput = document.getElementById('reg-nombres');
const paternoInput = document.getElementById('reg-paterno');
const maternoInput = document.getElementById('reg-materno');
const tipoDocInput = document.getElementById('reg-tipodoc');
const numDocInput = document.getElementById('reg-numdoc');
const emailInput = document.getElementById('reg-email');
const passInput = document.getElementById('reg-pass');
const submitBtn = document.getElementById('btn-submit-reg');

function clearErrors() {
    form.querySelectorAll('.elx-field-error').forEach(el => el.remove());
    [nombresInput, paternoInput, maternoInput, numDocInput, emailInput, passInput].forEach(input => {
        if (input) {
            input.classList.remove('border-red-500', 'focus:border-red-500');
            input.classList.add('border-neutral-300', 'focus:border-[#3564C9]');
        }
    });
}

function setFieldError(input, message) {
    if (!input) return;
    input.classList.remove('border-neutral-300', 'focus:border-[#3564C9]');
    input.classList.add('border-red-500', 'focus:border-red-500');

    const parent = input.closest('div');
    if (parent && !parent.querySelector('.elx-field-error')) {
        const errorEl = document.createElement('p');
        errorEl.className = 'elx-field-error text-[11px] text-red-600 mt-1 font-medium';
        errorEl.textContent = message;
        parent.appendChild(errorEl);
    }
}

// Configuración y validación dinámica de documento según tipo (DNI o CE)
function actualizarConfigDocumento() {
    const esDni = tipoDocInput.value === 'DNI';
    numDocInput.setAttribute('maxlength', esDni ? '8' : '9');
    numDocInput.setAttribute('placeholder', esDni ? '8 dígitos (ej. 12345678)' : '9 caracteres alfanuméricos');
    numDocInput.setAttribute('inputmode', esDni ? 'numeric' : 'text');

    if (esDni) {
        numDocInput.value = numDocInput.value.replace(/\D/g, '').slice(0, 8);
    } else {
        numDocInput.value = numDocInput.value.replace(/[^a-zA-Z0-9]/g, '').slice(0, 9);
    }
}

tipoDocInput.addEventListener('change', actualizarConfigDocumento);
numDocInput.addEventListener('input', () => {
    if (tipoDocInput.value === 'DNI') {
        numDocInput.value = numDocInput.value.replace(/\D/g, '');
    }
});
actualizarConfigDocumento();

// Limpiar estados de error al escribir
[nombresInput, paternoInput, maternoInput, numDocInput, emailInput, passInput].forEach(inp => {
    if (!inp) return;
    inp.addEventListener('input', () => {
        inp.classList.remove('border-red-500', 'focus:border-red-500');
        inp.classList.add('border-neutral-300', 'focus:border-[#3564C9]');
        const parent = inp.closest('div');
        const err = parent?.querySelector('.elx-field-error');
        if (err) err.remove();
    });
});

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearErrors();

    const nombres = nombresInput.value.trim();
    const paterno = paternoInput.value.trim();
    const materno = maternoInput.value.trim();
    const tipoDoc = tipoDocInput.value;
    const numDoc = numDocInput.value.trim();
    const email = emailInput.value.trim();
    const password = passInput.value;

    let hasErrors = false;

    if (!nombres || nombres.length < 2) {
        setFieldError(nombresInput, 'Los nombres deben tener al menos 2 caracteres.');
        hasErrors = true;
    }

    if (!paterno || paterno.length < 2) {
        setFieldError(paternoInput, 'El apellido paterno debe tener al menos 2 caracteres.');
        hasErrors = true;
    }

    if (tipoDoc === 'DNI' && numDoc.length !== 8) {
        setFieldError(numDocInput, 'El DNI debe tener exactamente 8 dígitos numéricos.');
        hasErrors = true;
    } else if (tipoDoc === 'CE' && numDoc.length !== 9) {
        setFieldError(numDocInput, 'El Carné de Extranjería debe tener exactamente 9 caracteres.');
        hasErrors = true;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!email || !emailRegex.test(email)) {
        setFieldError(emailInput, 'Ingresa un correo electrónico válido.');
        hasErrors = true;
    }

    if (!password || password.length < 6) {
        setFieldError(passInput, 'La contraseña debe tener al menos 6 caracteres.');
        hasErrors = true;
    }

    if (hasErrors) {
        return;
    }

    submitBtn.disabled = true;
    submitBtn.innerHTML = `
        <div class="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
        <span>Creando cuenta...</span>
    `;

    try {
        const datos = {
            nombres,
            apellidoPaterno: paterno,
            apellidoMaterno: materno || null,
            tipoDocumento: tipoDoc,
            numeroDocumento: numDoc,
            email,
            password
        };

        await auth.registro(datos);

        setTimeout(() => {
            window.location.href = '/index.html';
        }, 800);

    } catch (err) {
        submitBtn.disabled = false;
        submitBtn.innerHTML = `<span>Registrarse</span>`;

        const fieldMap = {
            nombres: nombresInput,
            apellidoPaterno: paternoInput,
            apellidoMaterno: maternoInput,
            tipoDocumento: tipoDocInput,
            numeroDocumento: numDocInput,
            email: emailInput,
            password: passInput
        };

        if (err.data && err.data.datos && typeof err.data.datos === 'object') {
            for (const [campo, mensaje] of Object.entries(err.data.datos)) {
                const targetInput = fieldMap[campo];
                if (targetInput) {
                    setFieldError(targetInput, mensaje);
                }
            }
        }
    }
});
